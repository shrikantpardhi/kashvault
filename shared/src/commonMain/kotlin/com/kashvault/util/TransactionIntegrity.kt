package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.FinanceSnapshot
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.displayLabel
import com.kashvault.domain.isIncome
import com.kashvault.domain.isSpend
import com.kashvault.domain.isTransfer
import kotlin.math.abs

enum class AnomalySeverity {
    WARNING,
    ERROR
}

data class DuplicateAlert(
    val primaryId: String,
    val duplicateId: String,
    val amount: Double,
    val sourceName: String,
    val description: String,
    val timestamp1: String,
    val timestamp2: String,
)

data class TransactionAnomaly(
    val expenseId: String,
    val issue: String,
    val severity: AnomalySeverity,
)

data class AccountAudit(
    val sourceId: String,
    val sourceName: String,
    val kind: String,
    val openingBalance: Double,
    val totalCredits: Double,
    val totalDebits: Double,
    val currentBalance: Double,
    val transactionCount: Int,
    val isMathematicallyVerified: Boolean,
    val isOverdrawn: Boolean,
)

data class PortfolioIntegrityReport(
    val isHealthy: Boolean,
    val totalTransactions: Int,
    val verifiedBalanceTotal: Double,
    val totalIncomeAcrossHistory: Double,
    val totalExpensesAcrossHistory: Double,
    val totalTransfersAcrossHistory: Double,
    val accountAudits: List<AccountAudit>,
    val duplicateAlerts: List<DuplicateAlert>,
    val anomalies: List<TransactionAnomaly>,
)

/**
 * Validates a single transaction candidate before insertion or update.
 * Returns a list of user-facing warnings or errors to enforce integrity.
 */
fun checkTransactionCandidate(
    candidate: Expense,
    existingExpenses: List<Expense>,
    sources: List<PaymentSource>,
): List<String> {
    val issues = mutableListOf<String>()

    if (!candidate.amount.isFinite() || candidate.amount <= 0.0) {
        issues += "Amount must be a positive number greater than 0."
    }

    if (candidate.isTransfer()) {
        if (candidate.destinationId.isNullOrBlank()) {
            issues += "Transfer requires a destination bank account or savings goal."
        } else if (candidate.destinationId == candidate.sourceId) {
            issues += "Source and destination account cannot be the same."
        }
    }

    if (!candidate.sourceId.isNullOrBlank() && sources.isNotEmpty()) {
        val found = sources.any { it.id == candidate.sourceId }
        if (!found) {
            issues += "Selected payment account does not exist in your account registry."
        }
    }

    // Duplicate detection: search for matching amount and account in existing expenses
    val candidateTime = parseIsoTimestampOrNull(candidate.timestamp)?.toEpochMilliseconds()
    val duplicateMatch = existingExpenses.firstOrNull { existing ->
        if (existing.id == candidate.id) return@firstOrNull false
        val sameAmount = abs(roundCurrency(existing.amount) - roundCurrency(candidate.amount)) < 0.005
        val sameType = (existing.type ?: "expense") == (candidate.type ?: "expense")
        val sameSource = existing.sourceId == candidate.sourceId && !candidate.sourceId.isNullOrBlank()

        if (sameAmount && sameType && sameSource) {
            val existingTime = parseIsoTimestampOrNull(existing.timestamp)?.toEpochMilliseconds()
            if (candidateTime != null && existingTime != null) {
                // Within 48 hours (172_800_000 ms)
                abs(candidateTime - existingTime) < 172_800_000L
            } else {
                true
            }
        } else {
            false
        }
    }

    if (duplicateMatch != null) {
        val srcName = sources.find { it.id == candidate.sourceId }?.displayLabel() ?: "account"
        issues += "Potential duplicate: An identical transaction of ${formatINR(candidate.amount)} was already recorded on $srcName (${formatTimestamp(duplicateMatch.timestamp)})."
    }

    return issues
}

/**
 * Runs a 360-degree audit across the entire transaction history, verifying:
 * 1. Mathematical consistency of every account ledger (Opening + Credits - Debits = Current Balance).
 * 2. Anomaly detection (corrupt amounts, invalid transfers, orphaned accounts).
 * 3. Duplicate transaction detection across history to protect against accidental double debits.
 */
fun auditPortfolioIntegrity(snapshot: FinanceSnapshot): PortfolioIntegrityReport {
    val expenses = snapshot.expenses
    val sources = snapshot.paymentSources
    val sourcesMap = sources.associateBy { it.id }

    val anomalies = mutableListOf<TransactionAnomaly>()
    val duplicateAlerts = mutableListOf<DuplicateAlert>()

    // 1. Audit individual expenses
    expenses.forEach { expense ->
        if (!expense.amount.isFinite() || expense.amount <= 0.0) {
            anomalies += TransactionAnomaly(
                expenseId = expense.id,
                issue = "Invalid amount: ${expense.amount}",
                severity = AnomalySeverity.ERROR,
            )
        }
        if (expense.isTransfer()) {
            if (expense.destinationId.isNullOrBlank()) {
                anomalies += TransactionAnomaly(
                    expenseId = expense.id,
                    issue = "Transfer is missing destination",
                    severity = AnomalySeverity.ERROR,
                )
            } else if (expense.destinationId == expense.sourceId) {
                anomalies += TransactionAnomaly(
                    expenseId = expense.id,
                    issue = "Transfer source and destination are identical",
                    severity = AnomalySeverity.WARNING,
                )
            }
        }
        if (!expense.sourceId.isNullOrBlank() && sources.isNotEmpty()) {
            if (!sourcesMap.containsKey(expense.sourceId)) {
                anomalies += TransactionAnomaly(
                    expenseId = expense.id,
                    issue = "Transaction references unlisted account ID: ${expense.sourceId}",
                    severity = AnomalySeverity.WARNING,
                )
            }
        }
    }

    // 2. Scan for duplicate pairs across entire history
    val sorted = expenses.sortedWith(compareBy<Expense> { it.timestamp }.thenBy { it.id })
    val seenPairs = mutableSetOf<String>()
    for (i in sorted.indices) {
        val a = sorted[i]
        if (!a.amount.isFinite() || a.amount <= 0.0) continue
        val aTime = parseIsoTimestampOrNull(a.timestamp)?.toEpochMilliseconds() ?: continue

        for (j in i + 1 until sorted.size) {
            val b = sorted[j]
            val bTime = parseIsoTimestampOrNull(b.timestamp)?.toEpochMilliseconds() ?: continue
            // If delta > 48 hours, break inner loop since list is sorted by timestamp
            if (bTime - aTime > 172_800_000L) break

            val sameAmount = abs(roundCurrency(a.amount) - roundCurrency(b.amount)) < 0.005
            val sameType = (a.type ?: "expense") == (b.type ?: "expense")
            val sameSource = a.sourceId == b.sourceId && !a.sourceId.isNullOrBlank()
            val sameNote = !a.note.isNullOrBlank() && a.note.trim().equals(b.note?.trim(), ignoreCase = true)

            if (sameAmount && sameType && (sameSource || sameNote)) {
                val pairKey = if (a.id < b.id) "${a.id}_${b.id}" else "${b.id}_${a.id}"
                if (pairKey !in seenPairs) {
                    seenPairs += pairKey
                    val srcName = sourcesMap[a.sourceId]?.displayLabel() ?: "Account"
                    val desc = a.note?.takeIf { it.isNotBlank() } ?: "Matching amount and account"
                    duplicateAlerts += DuplicateAlert(
                        primaryId = a.id,
                        duplicateId = b.id,
                        amount = roundCurrency(a.amount),
                        sourceName = srcName,
                        description = desc,
                        timestamp1 = a.timestamp,
                        timestamp2 = b.timestamp,
                    )
                }
            }
        }
    }

    // 3. Audit each account's double-entry ledger equation
    val accountAudits = sources.map { source ->
        val ledger = calculateAccountLedger(source, expenses)
        val isCard = source.kind == Constants.SOURCE_CREDIT_CARD
        val totalCredits = roundCurrency(ledger.filter { it.isCredit }.sumOf { it.displayAmount })
        val totalDebits = roundCurrency(ledger.filter { !it.isCredit }.sumOf { it.displayAmount })
        val currentBalance = sourceDisplayedBalance(source, expenses)

        // Mathematical equation check:
        // For asset account: current = opening + credits - debits
        // For credit card liability: current = opening + debits - credits
        val expectedBalance = if (isCard) {
            roundCurrency(source.openingBalance + totalDebits - totalCredits)
        } else {
            roundCurrency(source.openingBalance + totalCredits - totalDebits)
        }
        val isMathematicallyVerified = abs(currentBalance - expectedBalance) < 0.01

        AccountAudit(
            sourceId = source.id,
            sourceName = source.displayLabel(),
            kind = source.kind,
            openingBalance = source.openingBalance,
            totalCredits = totalCredits,
            totalDebits = totalDebits,
            currentBalance = currentBalance,
            transactionCount = ledger.size,
            isMathematicallyVerified = isMathematicallyVerified,
            isOverdrawn = !isCard && currentBalance < 0.0,
        )
    }

    val totalIncome = roundCurrency(expenses.filter { it.isIncome() }.sumOf { it.amount })
    val totalExpenses = roundCurrency(expenses.filter { it.isSpend() }.sumOf { it.amount })
    val totalTransfers = roundCurrency(expenses.filter { it.isTransfer() }.sumOf { it.amount })
    val totalCash = roundCurrency(
        sources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }
            .sumOf { sourceDisplayedBalance(it, expenses) }
    )

    val isHealthy = anomalies.none { it.severity == AnomalySeverity.ERROR } &&
        accountAudits.all { it.isMathematicallyVerified }

    return PortfolioIntegrityReport(
        isHealthy = isHealthy,
        totalTransactions = expenses.size,
        verifiedBalanceTotal = totalCash,
        totalIncomeAcrossHistory = totalIncome,
        totalExpensesAcrossHistory = totalExpenses,
        totalTransfersAcrossHistory = totalTransfers,
        accountAudits = accountAudits,
        duplicateAlerts = duplicateAlerts,
        anomalies = anomalies,
    )
}
