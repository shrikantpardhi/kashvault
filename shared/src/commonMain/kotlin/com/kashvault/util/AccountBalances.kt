package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.isIncome
import com.kashvault.domain.isSpend
import com.kashvault.domain.isTransfer

fun sourceDisplayedBalance(source: PaymentSource, expenses: List<Expense>): Double {
    val card = source.kind == Constants.SOURCE_CREDIT_CARD
    var balance = source.openingBalance
    expenses.forEach { expense ->
        if (expense.isTransfer()) {
            if (expense.sourceId == source.id) {
                balance = if (card) balance + expense.amount else balance - expense.amount
            }
            if (expense.destinationId == source.id) {
                balance = if (card) balance - expense.amount else balance + expense.amount
            }
        } else if (expense.sourceId == source.id) {
            balance += when {
                card && expense.isIncome() -> -expense.amount
                card && expense.isSpend() -> expense.amount
                !card && expense.isIncome() -> expense.amount
                !card && expense.isSpend() -> -expense.amount
                else -> 0.0
            }
        } else if (expense.isIncome() && expense.destinationId == source.id) {
            balance += if (card) -roundCurrency(expense.amount) else roundCurrency(expense.amount)
        }
    }
    return roundCurrency(balance)
}

data class AccountLedgerEntry(
    val expense: Expense,
    val isCredit: Boolean,
    val isTransfer: Boolean,
    val displayAmount: Double,
    val runningBalance: Double,
)

fun calculateAccountLedger(source: PaymentSource, expenses: List<Expense>): List<AccountLedgerEntry> {
    val card = source.kind == Constants.SOURCE_CREDIT_CARD
    val relevant = expenses.filter {
        it.sourceId == source.id || it.destinationId == source.id
    }.sortedWith(compareBy<Expense> { it.timestamp }.thenBy { it.id })

    var balance = source.openingBalance
    val entries = mutableListOf<AccountLedgerEntry>()

    relevant.forEach { expense ->
        val isCredit: Boolean
        val isTransfer = expense.isTransfer()
        val amount = roundCurrency(expense.amount)
        if (isTransfer) {
            if (expense.sourceId == source.id && expense.destinationId == source.id) {
                // Internal transfer on same account
                isCredit = false
            } else if (expense.sourceId == source.id) {
                // Outflow from this source
                isCredit = false
                balance = if (card) balance + amount else balance - amount
            } else {
                // Inflow into this source
                isCredit = true
                balance = if (card) balance - amount else balance + amount
            }
        } else if (expense.sourceId == source.id) {
            if (card) {
                if (expense.isIncome()) {
                    isCredit = true
                    balance -= amount
                } else {
                    isCredit = false
                    balance += amount
                }
            } else {
                if (expense.isIncome()) {
                    isCredit = true
                    balance += amount
                } else {
                    isCredit = false
                    balance -= amount
                }
            }
        } else if (expense.isIncome() && expense.destinationId == source.id) {
            isCredit = true
            balance += if (card) -amount else amount
        } else {
            isCredit = false
        }

        entries += AccountLedgerEntry(
            expense = expense.copy(amount = amount),
            isCredit = isCredit,
            isTransfer = isTransfer,
            displayAmount = amount,
            runningBalance = roundCurrency(balance),
        )
    }

    return entries.reversed()
}


fun openingBalanceForDisplayed(source: PaymentSource, expenses: List<Expense>, displayed: Double): Double {
    val current = sourceDisplayedBalance(source, expenses)
    return roundCurrency(source.openingBalance + (displayed - current))
}

fun availableCash(sources: List<PaymentSource>, expenses: List<Expense>): Double =
    roundCurrency(
        sources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }
            .sumOf { sourceDisplayedBalance(it, expenses) },
    )

fun cardOutstandingTotal(sources: List<PaymentSource>, expenses: List<Expense>): Double =
    roundCurrency(
        sources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }
            .sumOf { sourceDisplayedBalance(it, expenses) }.coerceAtLeast(0.0),
    )

fun outstandingCards(sources: List<PaymentSource>, expenses: List<Expense>): List<Pair<PaymentSource, Double>> =
    sources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }
        .map { it to sourceDisplayedBalance(it, expenses) }
        .filter { it.second > 0.0 }

data class MonthPulse(
    val income: Double,
    val spend: Double,
    val leftover: Double,
    val topCategories: List<Pair<String, Double>>,
)

fun monthPulse(
    expenses: List<Expense>,
    baseDate: kotlinx.datetime.LocalDate = todayLocalDate(),
    categoryName: (String) -> String,
): MonthPulse {
    val month = getFilteredExpenses(expenses, "monthly", baseDate)
    val income = month.filter { it.isIncome() }.sumOf { it.amount }
    val spend = month.filter { it.isSpend() }.sumOf { it.amount }
    val top = month.filter { it.isSpend() }
        .groupBy { it.categoryId }
        .map { (id, rows) -> categoryName(id) to rows.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .take(3)
    return MonthPulse(income, spend, income - spend, top)
}
