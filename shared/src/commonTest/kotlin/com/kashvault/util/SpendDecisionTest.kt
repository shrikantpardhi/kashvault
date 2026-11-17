package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.SyncSettings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpendDecisionTest {
    @Test
    fun homeDecisionShowsInboxBackupAndCardDue() {
        val today = LocalDate(2026, 8, 20)
        val card = PaymentSource(
            id = "c1",
            name = "HDFC Millennia",
            kind = Constants.SOURCE_CREDIT_CARD,
            statementDueDate = "2026-08-25",
            openingBalance = 2_500.0,
        )
        val next = nextCreditCardDue(listOf(card), emptyList(), today)
        assertEquals("HDFC Millennia", next?.name)
        assertEquals(5, next?.daysUntil)
        val decision = spendDecision(
            input = LiquidityInput(currentLiquidity = 10_000.0, monthlyIncome = 0.0, monthlyAvgSpend = 1_000.0, upcomingLien = 0.0),
            inboxCount = 2,
            cardBills = 0.0,
            nextCard = next,
            sync = SyncSettings(autoSync = false),
            today = today,
        )
        assertEquals("2 payments waiting", decision.inboxLabel)
        assertTrue(decision.backupNeedsAction)
        assertEquals("No backup yet", decision.backupLabel)
        assertTrue(decision.cardLabel.orEmpty().contains("HDFC Millennia"))
    }
}
