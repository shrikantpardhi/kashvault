package com.kashvault.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

const val WEEK_STARTS_ON = 1 // Monday

fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun todayInputValue(): String = todayLocalDate().toString()

fun parseIsoTimestamp(timestamp: String): Instant = Instant.parse(timestamp)

fun parseIsoTimestampOrNull(timestamp: String): Instant? =
    runCatching { Instant.parse(timestamp) }.getOrNull()

fun formatTimestamp(timestamp: String): String {
    val instant = parseIsoTimestampOrNull(timestamp) ?: return timestamp
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "${dt.date} $hour:$minute"
}

fun getDueMonthIndex(dueDate: String): Int {
    val parts = dueDate.split("-")
    return if (parts.size >= 2) parts[1].toIntOrNull()?.minus(1) ?: 0 else 0
}

fun startOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

fun endOfMonth(date: LocalDate): LocalDate {
    val nextMonth = if (date.monthNumber == 12) {
        LocalDate(date.year + 1, 1, 1)
    } else {
        LocalDate(date.year, date.monthNumber + 1, 1)
    }
    return nextMonth.minus(DatePeriod(days = 1))
}

fun isWithinMonth(timestamp: String, baseDate: LocalDate): Boolean {
    val instant = parseIsoTimestampOrNull(timestamp) ?: return false
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return dt.year == baseDate.year && dt.month == baseDate.month
}

fun monthsAgo(count: Int): LocalDate {
    val today = todayLocalDate()
    var year = today.year
    var month = today.monthNumber - count
    while (month <= 0) {
        month += 12
        year -= 1
    }
    return LocalDate(year, month, 1)
}

fun parseLocalDateOrNull(value: String?): LocalDate? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalDate.parse(trimmed.take(10)) }.getOrNull()
}

fun timestampOnDate(date: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val now = Clock.System.now().toLocalDateTime(zone)
    val time = if (date == now.date) now.time else LocalTime(12, 0)
    return LocalDateTime(date, time).toInstant(zone).toString()
}

fun localDateFromUtcMillis(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

fun utcMillisFromLocalDate(date: LocalDate): Long =
    LocalDateTime(date, LocalTime(0, 0)).toInstant(TimeZone.UTC).toEpochMilliseconds()

fun formatDisplayDate(date: LocalDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    return "${date.dayOfMonth.toString().padStart(2, '0')} ${months[date.monthNumber - 1]} ${date.year}"
}

fun formatMonthYear(date: LocalDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    return "${months[date.monthNumber - 1]} ${date.year}"
}

fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

fun dueDateOnDay(day: Int, today: LocalDate = todayLocalDate()): String {
    val clamped = clampDayOfMonth(today.year, today.monthNumber, day)
    return LocalDate(today.year, today.monthNumber, clamped).toString()
}

fun backupIsStale(lastBackupAt: String?, today: LocalDate = todayLocalDate(), days: Int = 7): Boolean {
    if (lastBackupAt.isNullOrBlank()) return true
    val date = parseLocalDateOrNull(lastBackupAt) ?: return true
    return today.toEpochDays() - date.toEpochDays() >= days
}

fun needsFirstRunSetup(setupCompleted: Boolean, sourceCount: Int, expenseCount: Int): Boolean {
    if (setupCompleted) return false
    return sourceCount == 0 && expenseCount == 0
}
