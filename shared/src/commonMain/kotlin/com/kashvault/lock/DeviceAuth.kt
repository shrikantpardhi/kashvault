package com.kashvault.lock

expect suspend fun requestDeviceAuth(prompt: String = "Unlock"): Boolean

expect fun isDeviceAuthAvailable(): Boolean
