package com.kashvault.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class DatabaseDriverFactory {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        return NativeSqliteDriver(KashVaultDatabase.Schema, "kashvault.db")
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun databaseFilePath(): String {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String
        return "${documents ?: "."}/kashvault.db"
    }
}
