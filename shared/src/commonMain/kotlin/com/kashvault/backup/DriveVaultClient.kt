package com.kashvault.backup

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
private const val KEEP_VERSIONS = 1
private const val DRIVE_PAGE_SIZE = 100
const val VAULT_BACKUP_PREFIX = "kashvault-"
const val VAULT_BACKUP_SUFFIX = ".db"
const val VAULT_BACKUP_LATEST_NAME = "kashvault-latest.db"

class DriveVaultClient(
    private val http: HttpClient = HttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listBackups(accessToken: String): List<CloudBackupFile> = fetchBackups(accessToken)

    suspend fun deleteAllBackups(accessToken: String) {
        val backups = fetchBackups(accessToken)
        val failures = mutableListOf<String>()
        backups.forEach { file ->
            try {
                val response = http.delete("$DRIVE_FILES/${file.id}") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                response.requireOk("Deleting a Google Drive database backup")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += "${file.name}: ${error.message ?: "deletion failed"}"
            }
        }
        if (failures.isNotEmpty()) {
            throw DatabaseBackupException(
                "Could not delete all Google Drive backups (${failures.size} of ${backups.size} failed): " +
                    failures.joinToString("; "),
            )
        }
    }

    private suspend fun fetchBackups(accessToken: String): List<CloudBackupFile> {
        val files = mutableListOf<CloudBackupFile>()
        var pageToken: String? = null
        do {
            val tokenForRequest = pageToken
            val response = http.get(DRIVE_FILES) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("spaces", "appDataFolder")
                parameter("orderBy", "modifiedTime desc")
                parameter("pageSize", DRIVE_PAGE_SIZE.toString())
                parameter("fields", "nextPageToken,files(id,name,modifiedTime,size)")
                if (!tokenForRequest.isNullOrBlank()) parameter("pageToken", tokenForRequest)
            }
            response.requireOk("Listing Drive database backups")
            val parsed = json.decodeFromString<DriveFileList>(response.bodyAsText())
            files += parsed.files.orEmpty()
                .filter { it.name.startsWith(VAULT_BACKUP_PREFIX) && it.name.endsWith(VAULT_BACKUP_SUFFIX) }
                .map {
                    CloudBackupFile(
                        id = it.id,
                        name = it.name,
                        modifiedTime = it.modifiedTime,
                        sizeBytes = it.size?.toLongOrNull(),
                    )
                }
            pageToken = parsed.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return files.sortedByDescending { it.modifiedTime.orEmpty() }
    }

    suspend fun uploadBackup(accessToken: String, filename: String, bytes: ByteArray) {
        assertSqliteDatabase(bytes)
        val existing = fetchBackups(accessToken).firstOrNull()
        if (existing != null) {
            val updated = http.patch("$DRIVE_UPLOAD/${existing.id}") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("uploadType", "media")
                parameter("fields", "id,name,modifiedTime")
                setBody(ByteArrayContent(bytes, ContentType.parse("application/vnd.sqlite3")))
            }
            updated.requireOk("Updating the SQLite vault on Drive")
        } else {
            val uploadName = filename.ifBlank { VAULT_BACKUP_LATEST_NAME }
            val metadata = """{"name":"$uploadName","parents":["appDataFolder"],"mimeType":"application/vnd.sqlite3"}"""
            val boundary = "kashvault_${bytes.size}"
            val body = buildMultipartRelated(boundary, metadata, bytes)
            val response = http.post(DRIVE_UPLOAD) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                parameter("uploadType", "multipart")
                parameter("fields", "id,name,modifiedTime")
                setBody(ByteArrayContent(body, ContentType.parse("multipart/related; boundary=$boundary")))
            }
            response.requireOk("Uploading the SQLite vault to Drive")
        }
        runCatching { pruneOldBackups(accessToken) }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        val response = http.get("$DRIVE_FILES/$fileId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("alt", "media")
        }
        response.requireOk("Downloading the Drive database backup")
        val bytes = response.bodyAsBytes()
        assertSqliteDatabase(bytes)
        return bytes
    }

    private suspend fun pruneOldBackups(accessToken: String) {
        val files = fetchBackups(accessToken)
        extraDriveBackups(files, KEEP_VERSIONS).forEach { file ->
            runCatching {
                val response = http.delete("$DRIVE_FILES/${file.id}") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                response.requireOk("Removing an old Drive database backup")
            }
        }
    }
}

@Serializable
private data class DriveFileList(
    val files: List<DriveFile>? = null,
    val nextPageToken: String? = null,
)

@Serializable
private data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val size: String? = null,
)

private suspend fun HttpResponse.requireOk(action: String) {
    if (status.isSuccess()) return
    val body = runCatching { bodyAsText() }.getOrNull().orEmpty().take(240)
    val hint = if (status.value == 401 || status.value == 403) {
        " Sign in again and allow Drive app-data access."
    } else {
        ""
    }
    throw DatabaseBackupException("$action failed (${status.value}).$hint ${body}".trim())
}

internal fun buildMultipartRelated(boundary: String, metadataJson: String, sqliteBytes: ByteArray): ByteArray {
    val preamble = buildString {
        append("--$boundary\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadataJson)
        append("\r\n--$boundary\r\n")
        append("Content-Type: application/vnd.sqlite3\r\n\r\n")
    }.encodeToByteArray()
    val closing = "\r\n--$boundary--\r\n".encodeToByteArray()
    return preamble + sqliteBytes + closing
}

internal fun extraDriveBackups(files: List<CloudBackupFile>, keep: Int = 1): List<CloudBackupFile> =
    files.drop(keep.coerceAtLeast(0))
