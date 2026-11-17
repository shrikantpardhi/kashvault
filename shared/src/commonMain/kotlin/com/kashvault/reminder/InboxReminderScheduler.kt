package com.kashvault.reminder

import com.kashvault.domain.InboxReminderSettings

interface InboxReminderScheduler {
    fun remindersSupported(): Boolean
    fun notificationPermissionGranted(): Boolean
    suspend fun requestNotificationPermission(): Boolean
    fun scheduleDailyReminder(settings: InboxReminderSettings)
    fun openNotificationSettings()
}

expect fun createInboxReminderScheduler(): InboxReminderScheduler
