package com.kashvault.profile

interface PlatformImagePicker {
    suspend fun pickImage(): ByteArray?
}

expect fun createPlatformImagePicker(): PlatformImagePicker
