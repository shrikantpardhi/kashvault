package com.kashvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kashvault.reminder.deliverInboxReminder

class InboxReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        deliverInboxReminder(context.applicationContext)
    }
}
