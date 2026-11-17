package com.kashvault.util

/**
 * Drops a second start for the same key while the first is in flight, and optionally
 * for a short cooldown after it finishes. Use one key per button/action.
 */
class ActionGate(
    private val nowMs: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    private val inFlight = hashSetOf<String>()
    private val lastFinishedAt = hashMapOf<String, Long>()

    fun tryStart(key: String, cooldownMs: Long = 0L): Boolean {
        if (key in inFlight) return false
        if (cooldownMs > 0L) {
            val last = lastFinishedAt[key] ?: 0L
            if (nowMs() - last < cooldownMs) return false
        }
        inFlight.add(key)
        return true
    }

    fun finish(key: String) {
        inFlight.remove(key)
        lastFinishedAt[key] = nowMs()
    }
}
