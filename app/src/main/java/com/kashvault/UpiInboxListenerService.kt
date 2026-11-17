package com.kashvault

import android.app.Notification
import android.os.Handler
import android.os.HandlerThread
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kashvault.domain.Constants
import com.kashvault.inbox.ingestAndroidInbox

class UpiInboxListenerService : NotificationListenerService() {
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("kashvault-upi-inbox").also { it.start() }
        workerThread = thread
        worker = Handler(thread.looper)
    }

    override fun onDestroy() {
        workerThread?.quitSafely()
        worker = null
        workerThread = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        val flags = sbn.notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = listOf(title, text, big).filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (body.isBlank()) return
        val sender = title.ifBlank { sbn.packageName }
        val capturedAt = sbn.postTime
        val appContext = applicationContext
        worker?.post {
            ingestAndroidInbox(
                context = appContext,
                channel = Constants.INBOX_NOTIFICATION,
                sender = sender,
                body = body,
                capturedAtMs = capturedAt,
            )
        }
    }
}
