package com.kashvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.Clock

/** Ignores extra taps on the same button key for [windowMs]. */
class SingleClicks(
    private val windowMs: Long,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val lastAt = mutableMapOf<String, Long>()

    operator fun invoke(key: String = "click", block: () -> Unit) {
        val now = nowMs()
        val previous = lastAt[key] ?: 0L
        if (now - previous < windowMs) return
        lastAt[key] = now
        block()
    }
}

@Composable
fun rememberSingleClick(windowMs: Long = 800L): SingleClicks =
    remember(windowMs) { SingleClicks(windowMs) }
