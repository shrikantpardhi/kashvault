package com.kashvault.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Run a PRAGMA that may return rows.
 *
 * Android's SQLiteDatabase rejects result-returning statements on
 * [SqlDriver.execute] with "Queries can be performed using SQLiteDatabase
 * query or rawQuery methods only." [SqlDriver.executeQuery] maps to those APIs.
 */
internal fun SqlDriver.executePragma(sql: String) {
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            while (cursor.next().value) {
                // Drain busy/log/checkpointed (WAL) or journal_mode / timeout rows.
            }
            QueryResult.Value(Unit)
        },
        parameters = 0,
    ).value
}
