package com.kashvault.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.kashvault.auth.AndroidAppContext
import com.kashvault.data.FinanceRepository
import com.kashvault.db.DatabaseDriverFactory
import com.kashvault.domain.InboxReminderSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val ACTION_INBOX_REMINDER = "com.kashvault.action.INBOX_REMINDER"
const val ACTION_OPEN_INBOX = "com.kashvault.action.OPEN_INBOX"
const val EXTRA_OPEN_INBOX = "com.kashvault.extra.OPEN_INBOX"

private const val CHANNEL_ID = "inbox_reminder"
private const val NOTIFICATION_ID = 2100
private const val ALARM_REQUEST_CODE = 2102
private const val CONTENT_REQUEST_CODE = 2101
private const val RECEIVER_CLASS = "com.kashvault.InboxReminderReceiver"
private const val MAIN_ACTIVITY_CLASS = "com.kashvault.MainActivity"

actual fun createInboxReminderScheduler(): InboxReminderScheduler = AndroidInboxReminderScheduler()

interface NotificationPermissionHost {
    fun requestPostNotificationsPermission(result: CompletableDeferred<Boolean>)
}

fun deliverInboxReminder(context: Context) {
    val app = context.applicationContext
    val repo = FinanceRepository(DatabaseDriverFactory(app))
    repo.ensureReady()
    val settings = repo.getInboxReminderSettings()
    scheduleInboxReminderAlarm(app, settings)
    if (!settings.enabled) return
    val pending = repo.countPendingInbox()
    if (pending <= 0) return
    showInboxReminderNotification(app, pending)
}

fun restoreInboxReminderAlarms(context: Context) {
    val app = context.applicationContext
    val repo = FinanceRepository(DatabaseDriverFactory(app))
    repo.ensureReady()
    scheduleInboxReminderAlarm(app, repo.getInboxReminderSettings())
}

internal fun scheduleInboxReminderAlarm(context: Context, settings: InboxReminderSettings) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pending = reminderAlarmPendingIntent(context)
    if (!settings.enabled) {
        alarmManager.cancel(pending)
        return
    }
    val triggerAt = nextInboxReminderEpochMs(settings.hour, settings.minute)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
}

private fun showInboxReminderNotification(context: Context, count: Int) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    ensureInboxReminderChannel(manager)
    val (title, body) = inboxReminderNotificationText(count)
    val content = PendingIntent.getActivity(
        context,
        CONTENT_REQUEST_CODE,
        Intent().setClassName(context.packageName, MAIN_ACTIVITY_CLASS)
            .setAction(ACTION_OPEN_INBOX)
            .putExtra(EXTRA_OPEN_INBOX, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val icon = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
    val notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(icon)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(content)
        .build()
    manager.notify(NOTIFICATION_ID, notification)
}

private fun ensureInboxReminderChannel(manager: NotificationManager) {
    val existing = manager.getNotificationChannel(CHANNEL_ID)
    if (existing != null) return
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Inbox reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nightly reminder to accept SMS payments waiting in your inbox"
        },
    )
}

private fun reminderAlarmPendingIntent(context: Context): PendingIntent {
    val intent = Intent().setClassName(context.packageName, RECEIVER_CLASS).setAction(ACTION_INBOX_REMINDER)
    return PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private class AndroidInboxReminderScheduler : InboxReminderScheduler {
    override fun remindersSupported(): Boolean = true

    override fun notificationPermissionGranted(): Boolean {
        val context = AndroidAppContext.appContext ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestNotificationPermission(): Boolean {
        if (notificationPermissionGranted()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val host = AndroidAppContext.activity as? NotificationPermissionHost ?: return false
        val result = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) { host.requestPostNotificationsPermission(result) }
        return result.await()
    }

    override fun scheduleDailyReminder(settings: InboxReminderSettings) {
        val context = AndroidAppContext.appContext ?: return
        scheduleInboxReminderAlarm(context, settings)
    }

    override fun openNotificationSettings() {
        val activity = AndroidAppContext.activity ?: return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
    }
}
