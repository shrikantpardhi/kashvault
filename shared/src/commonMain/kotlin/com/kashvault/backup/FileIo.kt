package com.kashvault.backup

expect fun readFileBytes(path: String): ByteArray

expect fun writeFileBytes(path: String, bytes: ByteArray)

expect fun deleteFileIfExists(path: String)

expect fun fileExists(path: String): Boolean

expect fun listFileNames(path: String): List<String>
