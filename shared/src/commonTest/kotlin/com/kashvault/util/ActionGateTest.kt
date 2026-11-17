package com.kashvault.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionGateTest {
    @Test
    fun secondStartIsBlockedUntilFinish() {
        val gate = ActionGate(nowMs = { 1_000L })
        assertTrue(gate.tryStart("save"))
        assertFalse(gate.tryStart("save"))
        gate.finish("save")
        assertTrue(gate.tryStart("save"))
    }

    @Test
    fun differentKeysDoNotBlockEachOther() {
        val gate = ActionGate(nowMs = { 1_000L })
        assertTrue(gate.tryStart("save"))
        assertTrue(gate.tryStart("delete"))
    }

    @Test
    fun cooldownBlocksImmediateRetry() {
        var now = 1_000L
        val gate = ActionGate(nowMs = { now })
        assertTrue(gate.tryStart("save", cooldownMs = 800L))
        gate.finish("save")
        now = 1_400L
        assertFalse(gate.tryStart("save", cooldownMs = 800L))
        now = 1_900L
        assertTrue(gate.tryStart("save", cooldownMs = 800L))
    }
}
