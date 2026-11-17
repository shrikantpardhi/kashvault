package com.kashvault.backup

import java.io.File

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun writeFileBytes(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeBytes(bytes)
}

actual fun deleteFileIfExists(path: String) {
    File(path).delete()
}

actual fun fileExists(path: String): Boolean = File(path).isFile

actual fun listFileNames(path: String): List<String> {
    val directory = File(path)
    if (!directory.exists()) return emptyList()
    if (!directory.isDirectory) throw IllegalStateException("Could not inspect local vault files.")
    return directory.list()?.toList()
        ?: throw IllegalStateException("Could not inspect local vault files.")
}
