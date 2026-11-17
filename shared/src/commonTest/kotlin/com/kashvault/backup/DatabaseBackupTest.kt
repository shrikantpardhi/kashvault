package com.kashvault.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseBackupTest {
    @Test
    fun rejectsJsonPayloads() {
        val json = """{"expenses":[],"categories":[]}""".encodeToByteArray()
        assertFailsWith<DatabaseBackupException> { assertSqliteDatabase(json) }
    }

    @Test
    fun rejectsTinyFiles() {
        val tiny = "SQLite format 3".encodeToByteArray() + byteArrayOf(0)
        assertFailsWith<DatabaseBackupException> { assertSqliteDatabase(tiny) }
    }

    @Test
    fun acceptsSqliteHeader() {
        val header = "SQLite format 3".encodeToByteArray() + ByteArray(100)
        header[15] = 0
        assertSqliteDatabase(header)
        assertTrue(header.size >= 100)
    }

    @Test
    fun multipartKeepsSqliteBytesIntact() {
        val db = "SQLite format 3".encodeToByteArray() + ByteArray(100)
        db[15] = 0
        val closing = "\r\n--bound--\r\n".encodeToByteArray()
        val body = buildMultipartRelated("bound", """{"name":"kashvault-test.db"}""", db)
        val extracted = body.copyOfRange(body.size - closing.size - db.size, body.size - closing.size)
        assertEquals(db.toList(), extracted.toList())
        assertTrue(body.decodeToString().contains("application/vnd.sqlite3"))
    }

    @Test
    fun keepsOnlyTheLatestDriveBackup() {
        fun file(id: String) = CloudBackupFile(id, "kashvault-$id.db", modifiedTime = id, sizeBytes = 1)
        val files = listOf(file("newest"), file("old"), file("older"))
        assertEquals(listOf("old", "older"), extraDriveBackups(files).map { it.id })
        assertTrue(extraDriveBackups(listOf(file("only"))).isEmpty())
        val many = (0 until 60).map { file("f$it") }
        assertEquals(59, extraDriveBackups(many).size)
        assertEquals("f0", many.first().id)
        assertEquals("f0", many.minus(extraDriveBackups(many).toSet()).single().id)
    }
}
