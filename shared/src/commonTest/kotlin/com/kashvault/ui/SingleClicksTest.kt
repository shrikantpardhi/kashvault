package com.kashvault.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SingleClicksTest {
    @Test
    fun sameKeyIgnoresClicksInsideTheWindow() {
        var now = 1_000L
        var count = 0
        val clicks = SingleClicks(windowMs = 800L, nowMs = { now })
        clicks("save") { count++ }
        now = 1_400L
        clicks("save") { count++ }
        assertEquals(1, count)
        now = 1_900L
        clicks("save") { count++ }
        assertEquals(2, count)
    }

    @Test
    fun differentKeysAreIndependent() {
        var now = 1_000L
        var save = 0
        var cancel = 0
        val clicks = SingleClicks(windowMs = 800L, nowMs = { now })
        clicks("save") { save++ }
        clicks("cancel") { cancel++ }
        assertEquals(1, save)
        assertEquals(1, cancel)
    }
}
