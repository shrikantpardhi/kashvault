package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.PaymentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TransactionFingerprintsTest {

    @Test
    fun fingerprintsAreDeterministicAndReflectCurrentRecordFields() {
        val expense = Expense(
            id = "exp-101",
            amount = 1500.0,
            categoryId = "groceries",
            timestamp = "2026-10-19T14:30:00Z",
            sourceId = "bank-hdfc",
            type = "expense",
            note = "Organic Market",
        )

        val first = TransactionFingerprints.fingerprint(expense)
        val second = TransactionFingerprints.fingerprint(expense)
        val modified = TransactionFingerprints.fingerprint(expense.copy(amount = 1500.01))

        assertEquals(first.sha256Hash, second.sha256Hash)
        assertEquals(first.referenceNumber, second.referenceNumber)
        assertEquals(64, first.sha256Hash.length)
        assertNotEquals(first.sha256Hash, modified.sha256Hash)
    }

    @Test
    fun transactionReceiptUsesInformationalReference() {
        val source = PaymentSource(
            id = "src-axis",
            name = "Axis Priority",
            kind = Constants.SOURCE_BANK,
            openingBalance = 50000.0,
        )
        val expense = Expense(
            id = "exp-303",
            amount = 12000.0,
            categoryId = "investments",
            timestamp = "2026-10-19T16:00:00Z",
            sourceId = source.id,
            type = "expense",
            note = "Index Fund SIP",
        )

        val receipt = TransactionFingerprints.buildTransactionReceipt(expense, listOf(source))

        assertTrue(receipt.voucherNumber.startsWith("KV-20261019-"))
        assertTrue(receipt.referenceNumber.startsWith("KV-"))
        assertEquals(12000.0, receipt.amount)
        assertEquals("🏦 Axis Priority", receipt.originAccountName)
        assertEquals(64, receipt.sha256Hash.length)
    }

    @Test
    fun ledgerDigestIsExplicitlyNotAnIntegrityVerification() {
        val expenses = listOf(
            Expense(id = "e1", amount = 100.0, categoryId = "cat1", timestamp = "2026-10-01T10:00:00Z", type = "income"),
            Expense(id = "e2", amount = 50.0, categoryId = "cat1", timestamp = "2026-10-02T10:00:00Z", type = "expense"),
            Expense(id = "e3", amount = 25.0, categoryId = "cat1", timestamp = "2026-10-03T10:00:00Z", type = Constants.TYPE_TRANSFER),
        )

        val summary = TransactionFingerprints.fingerprintLedger(expenses)

        assertEquals(3, summary.totalTransactions)
        assertEquals(64, summary.combinedDigest.length)
        assertTrue(summary.description.contains("do not prove authenticity or detect edits"))
    }
}
