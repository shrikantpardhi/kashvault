package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.PaymentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountBalancesTest {
    private val bank = PaymentSource("b1", "HDFC", Constants.SOURCE_BANK, openingBalance = 10_000.0)
    private val card = PaymentSource("c1", "Ace", Constants.SOURCE_CREDIT_CARD, openingBalance = 0.0)

    @Test
    fun cardSpendDoesNotReduceBankCash() {
        val expenses = listOf(
            Expense("e1", 500.0, "cat_food", "2026-08-01T00:00:00Z", type = "expense", sourceId = "c1"),
        )
        assertEquals(10_000.0, sourceDisplayedBalance(bank, expenses))
        assertEquals(500.0, sourceDisplayedBalance(card, expenses))
        assertEquals(10_000.0, availableCash(listOf(bank, card), expenses))
        assertEquals(500.0, cardOutstandingTotal(listOf(bank, card), expenses))
    }

    @Test
    fun bankSpendAndIncomeMoveCash() {
        val expenses = listOf(
            Expense("e1", 1_000.0, "cat_food", "2026-08-01T00:00:00Z", type = "expense", sourceId = "b1"),
            Expense("e2", 2_000.0, "cat_salary", "2026-08-02T00:00:00Z", type = "income", sourceId = "b1"),
        )
        assertEquals(11_000.0, sourceDisplayedBalance(bank, expenses))
    }

    @Test
    fun transferMovesCashBetweenBankAndCard() {
        val owed = card.copy(openingBalance = 1_500.0)
        val expenses = listOf(
            Expense(
                "t1",
                1_500.0,
                "cat_wealth",
                "2026-08-03T00:00:00Z",
                type = Constants.TYPE_TRANSFER,
                sourceId = "b1",
                destinationId = "c1",
            ),
        )
        assertEquals(8_500.0, sourceDisplayedBalance(bank, expenses))
        assertEquals(0.0, sourceDisplayedBalance(owed, expenses))
        assertEquals(8_500.0, availableCash(listOf(bank, owed), expenses))
    }

    @Test
    fun monthPulseIgnoresTransfers() {
        val expenses = listOf(
            Expense("i1", 20_000.0, "cat_salary", "2026-08-01T00:00:00Z", type = "income"),
            Expense("s1", 3_000.0, "cat_food", "2026-08-02T00:00:00Z", type = "expense"),
            Expense(
                "t1",
                4_000.0,
                "cat_wealth",
                "2026-08-03T00:00:00Z",
                type = Constants.TYPE_TRANSFER,
                sourceId = "b1",
                destinationId = Constants.DEST_EMERGENCY,
            ),
        )
        val pulse = monthPulse(expenses, kotlinx.datetime.LocalDate(2026, 8, 20)) { "Food" }
        assertEquals(20_000.0, pulse.income)
        assertEquals(3_000.0, pulse.spend)
        assertEquals(17_000.0, pulse.leftover)
    }

    @Test
    fun outstandingCardsListsOnlyPositiveBalances() {
        val paidOff = card.copy(id = "c2", name = "Zero")
        val expenses = listOf(
            Expense("e1", 500.0, "cat_food", "2026-08-01T00:00:00Z", type = "expense", sourceId = "c1"),
        )
        val rows = outstandingCards(listOf(bank, card, paidOff), expenses)
        assertEquals(1, rows.size)
        assertEquals("c1", rows.first().first.id)
        assertEquals(500.0, rows.first().second)
    }

    @Test
    fun cardSpendAddsOnlyToThatCardOutstanding() {
        val other = card.copy(id = "c2", name = "SBI", openingBalance = 200.0)
        val expenses = listOf(
            Expense("e1", 500.0, "cat_food", "2026-08-01T00:00:00Z", type = "expense", sourceId = "c1"),
        )
        assertEquals(500.0, sourceDisplayedBalance(card, expenses))
        assertEquals(200.0, sourceDisplayedBalance(other, expenses))
        assertEquals(700.0, cardOutstandingTotal(listOf(bank, card, other), expenses))
        assertEquals(10_000.0, availableCash(listOf(bank, card, other), expenses))
    }
}

class DueLoopTest {
    private val today = kotlinx.datetime.LocalDate(2026, 8, 20)

    @Test
    fun autoSkipsFutureDueThisMonth() {
        val due = openDueOn("2026-08-25", "Monthly", lastProcessedMonth = null, today = today, includeFuture = false)
        assertEquals(null, due)
        val early = openDueOn("2026-08-25", "Monthly", lastProcessedMonth = null, today = today, includeFuture = true)
        assertEquals(kotlinx.datetime.LocalDate(2026, 8, 25), early)
    }

    @Test
    fun autoPostsWhenDueDateHasArrived() {
        val due = openDueOn("2026-08-05", "Monthly", lastProcessedMonth = null, today = today, includeFuture = false)
        assertEquals(kotlinx.datetime.LocalDate(2026, 8, 5), due)
    }

    @Test
    fun alreadyProcessedThisMonthIsSkipped() {
        val due = openDueOn("2026-08-05", "Monthly", lastProcessedMonth = "2026-08", today = today, includeFuture = false)
        assertEquals(null, due)
        val early = openDueOn("2026-08-05", "Monthly", lastProcessedMonth = "2026-08", today = today, includeFuture = true)
        assertEquals(null, early)
    }

    @Test
    fun autoRequiresAPaymentSource() {
        assertEquals(null, resolveDuePaymentSourceId(null, null))
        assertEquals("b1", resolveDuePaymentSourceId(null, "b1"))
        assertEquals("sip-bank", resolveDuePaymentSourceId("sip-bank", "b1"))
    }
}

class TransferAnalyticsTest {
    @Test
    fun transfersAreNotSpendOrIncome() {
        val expenses = listOf(
            Expense("i1", 10_000.0, "cat_salary", "2026-08-01T00:00:00Z", type = "income"),
            Expense("s1", 2_000.0, "cat_food", "2026-08-02T00:00:00Z", type = "expense"),
            Expense("t1", 1_000.0, "cat_wealth", "2026-08-03T00:00:00Z", type = Constants.TYPE_TRANSFER),
        )
        val breakdown = getNetWorthBreakdown(
            NetWorthInput(
                expenses = expenses,
                investments = emptyList(),
                emergencyFund = com.kashvault.domain.EmergencyFund(6, 0.0, 0.0),
                savingsGoals = emptyList(),
                borrowLend = emptyList(),
                loans = emptyList(),
            ),
        )
        assertEquals(10_000.0, breakdown.totalIncome)
        assertEquals(2_000.0, breakdown.totalExpenses)
        assertTrue(breakdown.liquidity > 0)
    }
}
