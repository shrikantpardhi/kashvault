package com.kashvault.reminder

import com.kashvault.domain.InboxReminderSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

private const val REQUEST_ID = "inbox-reminder"

actual fun createInboxReminderScheduler(): InboxReminderScheduler = IosInboxReminderScheduler()

private class IosInboxReminderScheduler : InboxReminderScheduler {
    @Volatile
    private var grantedCache: Boolean? = null

    override fun remindersSupported(): Boolean = true

    override fun notificationPermissionGranted(): Boolean = grantedCache ?: false

    override suspend fun requestNotificationPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { granted, _ ->
                grantedCache = granted
                if (continuation.isActive) continuation.resume(granted)
            }
        }

    override fun scheduleDailyReminder(settings: InboxReminderSettings) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        refreshGrantedCache(center)
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_ID))
        if (!settings.enabled) return
        val content = UNMutableNotificationContent()
        content.setTitle("Inbox waiting")
        content.setBody("Review SMS payments and accept them into your vault.")
        content.setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        val components = NSDateComponents()
        components.hour = settings.hour.toLong()
        components.minute = settings.minute.toLong()
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
        val request = UNNotificationRequest.requestWithIdentifier(REQUEST_ID, content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    override fun openNotificationSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }

    private fun refreshGrantedCache(center: UNUserNotificationCenter) {
        center.getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
            val status = settings?.authorizationStatus
            grantedCache = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
        }
    }
}
