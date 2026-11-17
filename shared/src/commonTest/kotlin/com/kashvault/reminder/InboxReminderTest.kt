package com.kashvault.reminder

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboxReminderTest {
    @Test
    fun defaultsToNinePmEnabled() {
        val settings = parseInboxReminderSettings(null, null, null)
        assertTrue(settings.enabled)
        assertEquals(21, settings.hour)
        assertEquals(0, settings.minute)
        assertEquals("9:00 PM", formatReminderClock(settings.hour, settings.minute))
    }

    @Test
    fun parsesStoredTimeAndCanTurnOff() {
        val settings = parseInboxReminderSettings("0", "8", "30")
        assertFalse(settings.enabled)
        assertEquals(8, settings.hour)
        assertEquals(30, settings.minute)
        assertEquals("8:30 AM", formatReminderClock(settings.hour, settings.minute))
    }

    @Test
    fun clampsInvalidHourAndMinute() {
        val settings = parseInboxReminderSettings("1", "99", "-4")
        assertEquals(21, settings.hour)
        assertEquals(0, settings.minute)
    }

    @Test
    fun nextReminderIsTodayWhenTimeHasNotPassed() {
        val now = LocalDateTime(2026, 8, 21, 20, 0, 0)
        val next = nextInboxReminderDateTime(21, 0, now)
        assertEquals(LocalDateTime(2026, 8, 21, 21, 0), next)
    }

    @Test
    fun nextReminderIsTomorrowWhenTimeHasPassed() {
        val now = LocalDateTime(2026, 8, 21, 21, 0, 0)
        val next = nextInboxReminderDateTime(21, 0, now)
        assertEquals(LocalDateTime(2026, 8, 22, 21, 0), next)
    }

    @Test
    fun midnightClockLabelUsesTwelveAm() {
        assertEquals("12:05 AM", formatReminderClock(0, 5))
        assertEquals("12:00 PM", formatReminderClock(12, 0))
    }

    @Test
    fun notificationCopyUsesSingularAndPlural() {
        assertEquals(
            "1 SMS payment is waiting to accept.",
            inboxReminderNotificationText(1).second,
        )
        assertEquals(
            "3 SMS payments are waiting to accept.",
            inboxReminderNotificationText(3).second,
        )
    }
}
