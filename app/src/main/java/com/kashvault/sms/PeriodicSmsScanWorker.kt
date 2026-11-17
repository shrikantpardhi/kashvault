package com.kashvault.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kashvault.MainActivity
import com.kashvault.data.FinanceRepository
import com.kashvault.db.DatabaseDriverFactory
import com.kashvault.inbox.scanAndroidSms
import com.kashvault.reminder.ACTION_OPEN_INBOX
import com.kashvault.reminder.EXTRA_OPEN_INBOX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PeriodicSmsScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repo = FinanceRepository(DatabaseDriverFactory(applicationContext))
            repo.ensureReady()
            if (!repo.periodicSmsScanEnabled()) return@withContext Result.success()

            val outcome = scanAndroidSms(
                context = applicationContext,
                sinceEpochMs = repo.lastSmsScanAtEpochMs(),
            )
            when {
                outcome.accessDenied -> {
                    Log.w("KashVaultSmsScan", "Periodic SMS scan skipped: READ_SMS permission is unavailable")
                    Result.failure()
                }
                outcome.scanFailed -> Result.retry()
                else -> {
                    if (outcome.added > 0 && repo.periodicSmsScanEnabled()) {
                        showNewInboxNotification(applicationContext, outcome.added)
                    }
                    Result.success()
                }
            }
        } catch (error: Exception) {
            Log.e("KashVaultSmsScan", "Periodic SMS scan failed", error)
            Result.retry()
        }
    }
}

private const val CHANNEL_ID = "periodic_sms_scan"
private const val NOTIFICATION_ID = 2200

private fun showNewInboxNotification(context: Context, count: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "SMS inbox updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Alerts after scheduled SMS scans find items to review"
            },
        )
    }
    val intent = Intent().setClass(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_INBOX)
        .putExtra(EXTRA_OPEN_INBOX, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val content = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val body = if (count == 1) "1 new payment message is ready to review." else "$count new payment messages are ready to review."
    val icon = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
    manager.notify(
        NOTIFICATION_ID,
        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("New inbox items")
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build(),
    )
}
