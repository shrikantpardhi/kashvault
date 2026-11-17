package com.kashvault.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentSourceTest {
    @Test
    fun last4KeepsFourDigits() {
        assertEquals("4321", normalizeLast4("xx12-34-321"))
        assertNull(normalizeLast4("12"))
        assertNull(normalizeLast4("abcd"))
    }

    @Test
    fun splitsBanksAndCards() {
        val hdfc = PaymentSource("1", "HDFC", Constants.SOURCE_BANK, last4 = "1111")
        val millennia = PaymentSource("2", "Millennia", Constants.SOURCE_CREDIT_CARD, last4 = "8888")
        val sbi = PaymentSource("3", "SBI", Constants.SOURCE_BANK)
        val sources = listOf(hdfc, millennia, sbi)
        assertEquals(listOf("HDFC", "SBI"), sources.banks().map { it.name })
        assertEquals(listOf("Millennia"), sources.creditCards().map { it.name })
        assertEquals("🏦 HDFC · 1111", hdfc.copy(icon = "🏦").displayLabel())
        assertEquals("Credit card", millennia.kindLabel())
    }

    @Test
    fun monthSpendGroupsBySource() {
        val bank = PaymentSource("b1", "HDFC", Constants.SOURCE_BANK)
        val card = PaymentSource("c1", "Axis Ace", Constants.SOURCE_CREDIT_CARD)
        val rows = spendBySourceThisMonth(
            expenses = listOf(
                Expense("e1", 100.0, "cat", "2026-08-01T00:00:00Z", type = "expense", sourceId = "b1"),
                Expense("e2", 50.0, "cat", "2026-08-02T00:00:00Z", type = "income", sourceId = "b1"),
                Expense("e3", 20.0, "cat", "2026-08-03T00:00:00Z", type = "expense", sourceId = "c1"),
            ),
            sources = listOf(bank, card),
            inMonth = { true },
        )
        assertEquals(2, rows.size)
        assertEquals(100.0, rows.first { it.source.id == "b1" }.spent)
        assertEquals(50.0, rows.first { it.source.id == "b1" }.received)
        assertEquals(20.0, rows.first { it.source.id == "c1" }.spent)
        assertTrue(rows.all { it.spent > 0 || it.received > 0 })
    }
}
