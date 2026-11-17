package com.kashvault.reminder

import com.kashvault.domain.InboxReminderSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

fun parseInboxReminderSettings(
    enabledRaw: String?,
    hourRaw: String?,
    minuteRaw: String?,
): InboxReminderSettings = InboxReminderSettings(
    enabled = enabledRaw != "0",
    hour = hourRaw?.toIntOrNull()?.takeIf { it in 0..23 } ?: 21,
    minute = minuteRaw?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0,
)

fun formatReminderClock(hour: Int, minute: Int): String {
    val clampedHour = hour.coerceIn(0, 23)
    val clampedMinute = minute.coerceIn(0, 59)
    val hour12 = when (val remainder = clampedHour % 12) {
        0 -> 12
        else -> remainder
    }
    val period = if (clampedHour < 12) "AM" else "PM"
    return "$hour12:${clampedMinute.toString().padStart(2, '0')} $period"
}

fun nextInboxReminderDateTime(
    hour: Int,
    minute: Int,
    now: LocalDateTime,
): LocalDateTime {
    val time = LocalTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val todayAt = LocalDateTime(now.date, time)
    return if (now < todayAt) todayAt else LocalDateTime(now.date.plus(DatePeriod(days = 1)), time)
}

fun nextInboxReminderEpochMs(
    hour: Int,
    minute: Int,
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long = nextInboxReminderDateTime(hour, minute, now.toLocalDateTime(zone))
    .toInstant(zone)
    .toEpochMilliseconds()

fun inboxReminderNotificationText(count: Int): Pair<String, String> {
    val title = "Inbox waiting"
    val body = if (count == 1) {
        "1 SMS payment is waiting to accept."
    } else {
        "$count SMS payments are waiting to accept."
    }
    return title to body
}
