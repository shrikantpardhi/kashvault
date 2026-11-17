package com.kashvault.backup

private val SQLITE_HEADER = "SQLite format 3".encodeToByteArray() + byteArrayOf(0)

open class DatabaseBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BackupCancelledException : DatabaseBackupException("Canceled")

fun assertSqliteDatabase(bytes: ByteArray) {
    if (bytes.size < 100) {
        throw DatabaseBackupException("That file is too small to be a KashVault database.")
    }
    if (bytes.size < SQLITE_HEADER.size || !bytes.copyOf(SQLITE_HEADER.size).contentEquals(SQLITE_HEADER)) {
        throw DatabaseBackupException("That file is not a SQLite database. KashVault backs up the vault file, not JSON.")
    }
}

data class CloudBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String?,
    val sizeBytes: Long?,
)
