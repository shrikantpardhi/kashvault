package com.kashvault.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(context: Context) {
    private val appContext = context.applicationContext

    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver {
        val driver = AndroidSqliteDriver(KashVaultDatabase.Schema, appContext, "kashvault.db")
        runCatching { driver.executePragma("PRAGMA journal_mode=WAL;") }
        runCatching { driver.executePragma("PRAGMA busy_timeout=5000;") }
        return driver
    }

    actual fun databaseFilePath(): String = appContext.getDatabasePath("kashvault.db").absolutePath
}
