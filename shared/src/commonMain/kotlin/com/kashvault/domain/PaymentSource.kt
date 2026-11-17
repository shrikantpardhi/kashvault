package com.kashvault.domain

fun PaymentSource.kindLabel(): String = when (kind) {
    Constants.SOURCE_CREDIT_CARD -> "Credit card"
    else -> "Bank"
}

fun PaymentSource.displayName(): String {
    val digits = last4?.trim()?.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
    return "$name$digits"
}

fun PaymentSource.displayLabel(): String = "${icon.ifBlank { defaultSourceIcon(kind) }} ${displayName()}"

fun defaultSourceIcon(kind: String): String =
    if (kind == Constants.SOURCE_CREDIT_CARD) "💳" else "🏦"

fun defaultSourceColor(kind: String): String =
    if (kind == Constants.SOURCE_CREDIT_CARD) "#845EF7" else "#339AF0"

fun normalizeLast4(raw: String?): String? {
    val digits = raw.orEmpty().filter { it.isDigit() }.takeLast(4)
    return digits.takeIf { it.length == 4 }
}

fun List<PaymentSource>.banks(): List<PaymentSource> =
    filter { it.kind != Constants.SOURCE_CREDIT_CARD }.sortedBy { it.name.lowercase() }

fun List<PaymentSource>.creditCards(): List<PaymentSource> =
    filter { it.kind == Constants.SOURCE_CREDIT_CARD }.sortedBy { it.name.lowercase() }

data class SourceSpend(
    val source: PaymentSource,
    val spent: Double,
    val received: Double,
)

fun spendBySourceThisMonth(
    expenses: List<Expense>,
    sources: List<PaymentSource>,
    inMonth: (String) -> Boolean,
): List<SourceSpend> {
    if (sources.isEmpty()) return emptyList()
    val byId = sources.associateBy { it.id }
    val spent = mutableMapOf<String, Double>()
    val received = mutableMapOf<String, Double>()
    expenses.filter { inMonth(it.timestamp) }.forEach { expense ->
        val sourceId = expense.sourceId ?: return@forEach
        if (sourceId !in byId) return@forEach
        if (expense.type == "income") {
            received[sourceId] = (received[sourceId] ?: 0.0) + expense.amount
        } else if (expense.type != Constants.TYPE_TRANSFER) {
            spent[sourceId] = (spent[sourceId] ?: 0.0) + expense.amount
        }
    }
    return sources.map { source ->
        SourceSpend(source, spent[source.id] ?: 0.0, received[source.id] ?: 0.0)
    }.filter { it.spent > 0.0 || it.received > 0.0 }
}
