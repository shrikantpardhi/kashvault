package com.kashvault.inbox

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.util.Log
import com.kashvault.auth.AndroidAppContext
import com.kashvault.data.FinanceRepository
import com.kashvault.db.DatabaseDriverFactory
import com.kashvault.domain.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

private const val LISTENER_CLASS = "com.kashvault.UpiInboxListenerService"

actual fun createInboxCapture(): InboxCapture = AndroidInboxCapture()

fun ingestAndroidInbox(
    context: Context,
    channel: String,
    sender: String,
    body: String,
    capturedAtMs: Long? = null,
): Boolean {
    if (body.isBlank()) return false
    return try {
        val repo = FinanceRepository(DatabaseDriverFactory(context.applicationContext))
        val capturedAt = capturedAtMs?.let { Instant.fromEpochMilliseconds(it).toString() }
            ?: kotlinx.datetime.Clock.System.now().toString()
        repo.ingestCapturedMessage(channel, sender, body, capturedAt) != null
    } catch (error: Exception) {
        Log.e("KashVaultInbox", "Failed to ingest captured message", error)
        false
    }
}

interface InboxPermissionHost {
    fun requestSmsPermission(result: CompletableDeferred<Boolean>)
}

private class AndroidInboxCapture : InboxCapture {
    override fun notificationCaptureSupported(): Boolean = true
    override fun smsCaptureSupported(): Boolean = true

    override fun notificationAccessEnabled(): Boolean {
        val context = AndroidAppContext.appContext ?: return false
        val component = listenerComponent(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.isNotificationListenerAccessGranted(component)
        } else {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            enabled.split(":").any { it.equals(component.flattenToString(), ignoreCase = true) }
        }
    }

    override fun openNotificationAccessSettings() {
        val activity = AndroidAppContext.activity ?: return
        val component = listenerComponent(activity)
        val generic = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val launched = runCatching {
            activity.startActivity(generic)
            true
        }.getOrDefault(false)
        if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        component.flattenToString(),
                    ),
                )
            }
        }
        runCatching { NotificationListenerService.requestRebind(component) }
    }

    override fun smsPermissionGranted(): Boolean {
        val context = AndroidAppContext.appContext ?: return false
        return context.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestSmsPermission(): Boolean {
        if (smsPermissionGranted()) return true
        val host = AndroidAppContext.activity as? InboxPermissionHost ?: return false
        val result = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) { host.requestSmsPermission(result) }
        return result.await()
    }

    override fun openSmsPermissionSettings() {
        val activity = AndroidAppContext.activity ?: return
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", activity.packageName, null),
        )
        runCatching { activity.startActivity(intent) }
    }

    override fun scanRecentSms(onProgress: (SmsScanProgress) -> Unit): SmsScanResult {
        val context = AndroidAppContext.appContext ?: return SmsScanResult(scanFailed = true)
        return scanAndroidSms(context, sinceEpochMs = null, onProgress = onProgress)
    }

    override fun schedulePeriodicSmsScan(enabled: Boolean): Boolean {
        val host = AndroidAppContext.activity as? SmsScanWorkSchedulerHost ?: return false
        host.setPeriodicSmsScanEnabled(enabled)
        return true
    }
}

fun scanAndroidSms(
    context: Context,
    sinceEpochMs: Long?,
    onProgress: (SmsScanProgress) -> Unit = {},
): SmsScanResult {
    val app = context.applicationContext
    if (app.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
        return SmsScanResult(accessDenied = true)
    }
    val repo = FinanceRepository(DatabaseDriverFactory(app))
    return try {
        repo.ensureReady()
        val rules = repo.getInboxScanRules()
        val cutoff = sinceEpochMs?.let { (it - SMS_SCAN_OVERLAP_MS).coerceAtLeast(0L) }
        val selection = cutoff?.let { "${Telephony.Sms.DATE} >= ?" }
        val selectionArgs = cutoff?.let { arrayOf(it.toString()) }
        val cursor = app.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} ASC",
        ) ?: return SmsScanResult(scanFailed = true)
        var scanned = 0
        var added = 0
        var reportedAdded = 0
        cursor.use { rows ->
            val senderIdx = columnIndex(rows, Telephony.Sms.ADDRESS, "address")
            val bodyIdx = columnIndex(rows, Telephony.Sms.BODY, "body")
            val dateIdx = columnIndex(rows, Telephony.Sms.DATE, "date")
            if (bodyIdx < 0 || dateIdx < 0) return SmsScanResult(scanFailed = true)
            while (rows.moveToNext()) {
                scanned += 1
                val sender = if (senderIdx >= 0) rows.getString(senderIdx).orEmpty() else ""
                val body = rows.getString(bodyIdx).orEmpty()
                if (!rules.matches(sender, body)) continue
                val capturedAt = Instant.fromEpochMilliseconds(rows.getLong(dateIdx)).toString()
                if (repo.ingestCapturedMessage(
                        Constants.INBOX_SMS,
                        sender,
                        body,
                        capturedAt,
                        scanRules = rules,
                    ) != null
                ) {
                    added += 1
                }
                if (scanned % 50 == 0 || added != reportedAdded) {
                    onProgress(SmsScanProgress(scanned = scanned, added = added))
                    reportedAdded = added
                }
            }
        }
        repo.setLastSmsScanAtEpochMs(System.currentTimeMillis())
        SmsScanResult(added = added)
    } catch (_: SecurityException) {
        SmsScanResult(accessDenied = true)
    } catch (error: Exception) {
        Log.e("KashVaultInbox", "SMS provider scan failed", error)
        SmsScanResult(scanFailed = true)
    }
}

private const val SMS_SCAN_OVERLAP_MS = 24L * 60 * 60 * 1000

private fun columnIndex(cursor: Cursor, vararg names: String): Int {
    names.forEach { name ->
        val index = cursor.getColumnIndex(name)
        if (index >= 0) return index
    }
    return -1
}

private fun listenerComponent(context: Context): ComponentName =
    ComponentName(context.packageName, LISTENER_CLASS)
