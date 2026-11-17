package com.kashvault.util

import com.kashvault.auth.sha256
import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.displayLabel
import com.kashvault.domain.isIncome
import com.kashvault.domain.isTransfer

fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun sha256Hex(text: String): String =
    sha256(text.encodeToByteArray()).toHex()

data class TransactionFingerprint(
    val expenseId: String,
    val sha256Hash: String,
    val shortHash: String,
    val referenceNumber: String,
    val canonicalPayload: String,
    val sequenceIndex: Int,
)

data class TransactionReceipt(
    val voucherNumber: String,
    val referenceNumber: String,
    val issuedAt: String,
    val timestampRaw: String,
    val originAccountName: String,
    val destinationAccountName: String?,
    val counterpartyOrCategory: String,
    val amount: Double,
    val typeLabel: String,
    val note: String,
    val sha256Hash: String,
)

data class LedgerFingerprintSummary(
    val totalTransactions: Int,
    val combinedDigest: String,
    val description: String,
)

object TransactionFingerprints {

    fun canonicalPayload(expense: Expense): String {
        val normalizedAmount = (expense.amount * 100.0).toLong()
        val src = expense.sourceId ?: "NONE"
        val dst = expense.destinationId ?: "NONE"
        val cat = expense.categoryId ?: "UNCATEGORIZED"
        val note = expense.note?.trim().orEmpty()
        val due = expense.dueEntityId ?: "NONE"
        return "ID:${expense.id}|TIME:${expense.timestamp}|AMT:$normalizedAmount|TYPE:${expense.type}|SRC:$src|DST:$dst|CAT:$cat|DUE:$due|NOTE:$note"
    }

    fun fingerprint(expense: Expense, allExpenses: List<Expense> = emptyList()): TransactionFingerprint {
        val payload = canonicalPayload(expense)
        val hash = sha256Hex(payload)
        val reference = "KV-${hash.take(8).uppercase()}"
        val sequence = if (allExpenses.isEmpty()) {
            1
        } else {
            val index = allExpenses.sortedBy { it.timestamp }.indexOfFirst { it.id == expense.id }
            if (index >= 0) index + 1 else allExpenses.size
        }
        return TransactionFingerprint(
            expenseId = expense.id,
            sha256Hash = hash,
            shortHash = hash.take(12),
            referenceNumber = reference,
            canonicalPayload = payload,
            sequenceIndex = sequence,
        )
    }

    fun buildTransactionReceipt(
        expense: Expense,
        sources: List<PaymentSource>,
        allExpenses: List<Expense> = emptyList(),
    ): TransactionReceipt {
        val fingerprint = fingerprint(expense, allExpenses)
        val originSource = sources.find { it.id == expense.sourceId }
        val destSource = sources.find { it.id == expense.destinationId }

        val originName = originSource?.displayLabel() ?: when {
            expense.isIncome() -> "External Deposit"
            expense.type == Constants.TYPE_LENDING -> "Direct Cash / Unlinked"
            else -> "Vault Primary"
        }
        val destName = destSource?.displayLabel()
        val typeLabel = when {
            expense.isTransfer() -> "INTER-ACCOUNT TRANSFER"
            expense.isIncome() -> "INCOME"
            expense.type == Constants.TYPE_LENDING -> "LOAN ADVANCE"
            expense.type == "borrow" -> "BORROWED FUNDS"
            else -> "EXPENSE"
        }
        val counterparty = if (expense.isTransfer()) {
            "Transfer to ${destName ?: "Destination Account"}"
        } else {
            expense.categoryId.replace("_", " ").uppercase()
        }

        return TransactionReceipt(
            voucherNumber = "KV-${expense.timestamp.take(10).replace("-", "")}-${fingerprint.sha256Hash.takeLast(4).uppercase()}",
            referenceNumber = fingerprint.referenceNumber,
            issuedAt = formatTimestamp(expense.timestamp),
            timestampRaw = expense.timestamp,
            originAccountName = originName,
            destinationAccountName = destName,
            counterpartyOrCategory = counterparty,
            amount = expense.amount,
            typeLabel = typeLabel,
            note = expense.note?.ifBlank { "No additional memo recorded" } ?: "No additional memo recorded",
            sha256Hash = fingerprint.sha256Hash,
        )
    }

    fun fingerprintLedger(expenses: List<Expense>): LedgerFingerprintSummary {
        val hashes = expenses.sortedBy { it.timestamp }
            .joinToString("") { fingerprint(it).sha256Hash }
        return LedgerFingerprintSummary(
            totalTransactions = expenses.size,
            combinedDigest = sha256Hex(hashes),
            description = "These SHA-256 fingerprints are calculated from current records. They do not prove authenticity or detect edits.",
        )
    }
}
