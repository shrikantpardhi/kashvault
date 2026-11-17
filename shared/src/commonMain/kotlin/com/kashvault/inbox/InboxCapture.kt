package com.kashvault.inbox

data class SmsScanResult(
    val added: Int = 0,
    val accessDenied: Boolean = false,
    val scanFailed: Boolean = false,
)

data class SmsScanProgress(
    val scanned: Int = 0,
    val added: Int = 0,
)

interface InboxCapture {
    fun notificationCaptureSupported(): Boolean
    fun smsCaptureSupported(): Boolean
    fun notificationAccessEnabled(): Boolean
    fun openNotificationAccessSettings()
    fun smsPermissionGranted(): Boolean
    suspend fun requestSmsPermission(): Boolean
    fun openSmsPermissionSettings()
    fun scanRecentSms(onProgress: (SmsScanProgress) -> Unit = {}): SmsScanResult
    fun schedulePeriodicSmsScan(enabled: Boolean): Boolean
}

interface SmsScanWorkSchedulerHost {
    fun setPeriodicSmsScanEnabled(enabled: Boolean)
}

expect fun createInboxCapture(): InboxCapture
