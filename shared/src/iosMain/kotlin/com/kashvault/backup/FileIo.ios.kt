package com.kashvault.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
    val length = data.length.toInt()
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
actual fun writeFileBytes(path: String, bytes: ByteArray) {
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    data.writeToFile(path, atomically = true)
}

actual fun deleteFileIfExists(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}

actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun listFileNames(path: String): List<String> {
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(path)) return emptyList()
    val names = fileManager.contentsOfDirectoryAtPath(path, null)
        ?: throw IllegalStateException("Could not inspect local vault files.")
    return names.mapNotNull { it as? String }
}
