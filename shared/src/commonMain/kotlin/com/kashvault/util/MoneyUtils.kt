package com.kashvault.util

import com.kashvault.domain.Constants

fun parseMoneyInput(value: String): Double {
    val trimmed = value.trim().replace(",", "")
    if (trimmed.isEmpty()) return Double.NaN

    if (trimmed.matches(Regex("^\\d+$"))) {
        return parseFiniteNumber(trimmed) ?: Double.NaN
    }

    val match = Regex("^(\\d+)(?:\\.(\\d{1,2}))?$").find(trimmed)
    if (match != null) {
        val rupees = parseFiniteNumber(match.groupValues[1]) ?: return Double.NaN
        val fractional = match.groupValues[2]
        if (fractional.isEmpty()) return rupees
        val paise = fractional.padEnd(2, '0').take(2).toIntOrNull() ?: return Double.NaN
        return rupees + paise / 100.0
    }

    val parsed = trimmed.toDoubleOrNull()
    if (parsed == null || !parsed.isFinite()) return Double.NaN
    return kotlin.math.round(parsed * 100) / 100.0
}

private fun parseFiniteNumber(digits: String): Double? =
    digits.toLongOrNull()?.toDouble()
        ?: digits.toDoubleOrNull()?.takeIf { it.isFinite() }

fun formatIndianGrouping(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val head = digits.dropLast(3)
    val pairs = buildString {
        var i = head.length
        while (i > 0) {
            val start = (i - 2).coerceAtLeast(0)
            if (isNotEmpty()) insert(0, ",")
            insert(0, head.substring(start, i))
            i = start
        }
    }
    return "$pairs,$lastThree"
}

fun roundCurrency(value: Double): Double =
    kotlin.math.round(value * 100.0) / 100.0

fun formatMoney(amount: Double, alwaysShowDecimals: Boolean = false): String {
    if (!amount.isFinite()) return "0"
    val roundedCents = kotlin.math.round(kotlin.math.abs(amount) * 100.0).toLong()
    val rupees = roundedCents / 100
    val paise = roundedCents % 100
    val sign = if (amount < 0 && roundedCents > 0) "-" else ""
    val rupeesStr = formatIndianGrouping(rupees.toString())
    return if (paise != 0L || alwaysShowDecimals) {
        val paiseStr = paise.toString().padStart(2, '0')
        "$sign$rupeesStr.$paiseStr"
    } else {
        "$sign$rupeesStr"
    }
}

fun formatINR(amount: Double, alwaysShowDecimals: Boolean = false): String =
    "${Constants.RUPEE_SIGN}${formatMoney(amount, alwaysShowDecimals)}"

fun formatINRSigned(amount: Double, type: String = "neutral", alwaysShowDecimals: Boolean = false): String {
    val abs = formatMoney(kotlin.math.abs(amount), alwaysShowDecimals)
    return when (type) {
        "income" -> "+ ${Constants.RUPEE_SIGN}$abs"
        "expense" -> "− ${Constants.RUPEE_SIGN}$abs"
        Constants.TYPE_TRANSFER -> "⇄ ${Constants.RUPEE_SIGN}$abs"
        else -> when {
            amount < 0 -> "− ${Constants.RUPEE_SIGN}$abs"
            amount > 0 -> "+ ${Constants.RUPEE_SIGN}$abs"
            else -> "${Constants.RUPEE_SIGN}$abs"
        }
    }
}

