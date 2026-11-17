package com.kashvault.backup

import com.kashvault.auth.AndroidAppContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createPlatformBackupActions(): PlatformBackupActions = AndroidBackupActions()

private class AndroidBackupActions : PlatformBackupActions {
    override suspend fun exportToUser(bytes: ByteArray, filename: String) {
        val host = AndroidAppContext.activity as? BackupDocumentHost
            ?: throw DatabaseBackupException("File picker is not ready.")
        val result = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            host.launchCreateDatabase(filename, bytes, result)
        }
        if (!result.await()) {
            throw BackupCancelledException()
        }
    }

    override suspend fun importFromUser(): ByteArray? {
        val host = AndroidAppContext.activity as? BackupDocumentHost
            ?: throw DatabaseBackupException("File picker is not ready.")
        val result = CompletableDeferred<ByteArray?>()
        withContext(Dispatchers.Main) {
            host.launchOpenDatabase(result)
        }
        return result.await()
    }
}

interface BackupDocumentHost {
    fun launchCreateDatabase(suggestedName: String, bytes: ByteArray, result: CompletableDeferred<Boolean>)
    fun launchOpenDatabase(result: CompletableDeferred<ByteArray?>)
}
