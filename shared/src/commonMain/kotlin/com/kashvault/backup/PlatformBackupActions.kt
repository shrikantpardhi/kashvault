package com.kashvault.backup

/** Share or pick a raw SQLite vault file on the device. */
interface PlatformBackupActions {
    suspend fun exportToUser(bytes: ByteArray, filename: String)
    suspend fun importFromUser(): ByteArray?
}

expect fun createPlatformBackupActions(): PlatformBackupActions
