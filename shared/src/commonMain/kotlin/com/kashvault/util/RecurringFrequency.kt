package com.kashvault.util

import com.kashvault.domain.Investment

fun normalizeInvestmentFrequency(inv: Investment): String =
    inv.frequency ?: if (inv.type == "SIP") "Monthly" else "OneTime"

fun isFrequencyDueInMonth(frequency: String, dueDate: String, currentMonth: Int): Boolean {
    if (frequency == "OneTime") return false
    if (frequency == "Monthly") return true

    val dueMonth = getDueMonthIndex(dueDate)
    if (frequency == "Yearly") return dueMonth == currentMonth

    val offset = (currentMonth - dueMonth + 12) % 12
    return offset % 3 == 0
}

fun frequencyLabel(frequency: String): String = when (frequency) {
    "Monthly" -> "Monthly"
    "Quarterly" -> "Quarterly"
    "Yearly" -> "Yearly"
    "OneTime" -> "One-time"
    else -> frequency
}

fun monthlyEquivalent(amount: Double, frequency: String): Double = when (frequency) {
    "Yearly" -> kotlin.math.round(amount / 12)
    "Quarterly" -> kotlin.math.round(amount / 3)
    "OneTime" -> 0.0
    else -> amount
}
