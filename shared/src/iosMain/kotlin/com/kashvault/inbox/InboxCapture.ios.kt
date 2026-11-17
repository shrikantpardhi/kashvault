package com.kashvault.inbox

actual fun createInboxCapture(): InboxCapture = IosInboxCapture()

private class IosInboxCapture : InboxCapture {
    override fun notificationCaptureSupported(): Boolean = false
    override fun smsCaptureSupported(): Boolean = false
    override fun notificationAccessEnabled(): Boolean = false
    override fun openNotificationAccessSettings() = Unit
    override fun smsPermissionGranted(): Boolean = false
    override suspend fun requestSmsPermission(): Boolean = false
    override fun openSmsPermissionSettings() = Unit
    override fun scanRecentSms(onProgress: (SmsScanProgress) -> Unit): SmsScanResult = SmsScanResult()
}
