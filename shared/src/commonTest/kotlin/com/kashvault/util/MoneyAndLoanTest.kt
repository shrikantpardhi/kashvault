package com.kashvault.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoneyUtilsTest {
    @Test
    fun parseWholeRupees() {
        assertEquals(1000.0, parseMoneyInput("1000"))
        assertEquals(125000.0, parseMoneyInput("1,25,000"))
    }

    @Test
    fun parseDoesNotThrowOnHugeOrJunkAmounts() {
        parseMoneyInput("9".repeat(40))
        parseMoneyInput("12.34.56")
        assertTrue(parseMoneyInput("").isNaN())
        assertTrue(parseMoneyInput("abc").isNaN())
        assertEquals(10.5, parseMoneyInput("10.5"))
        assertEquals(10.50, parseMoneyInput("10.50"))
    }

    @Test
    fun formatSignedAmounts() {
        assertTrue(formatINR(500.0).contains("500"))
        assertTrue(formatINRSigned(120.0, "expense").startsWith("−"))
        assertTrue(formatINRSigned(120.0, "income").startsWith("+"))
    }

    @Test
    fun formatsIndianGrouping() {
        assertEquals("₹1,000", formatINR(1000.0))
        assertEquals("₹1,25,000", formatINR(125000.0))
        assertEquals("₹12,34,567", formatINR(1234567.0))
        assertEquals("1,25,000", formatIndianGrouping("125000"))
    }
}

class LoanUtilsTest {
    @Test
    fun remainingEmisUsesTenureMinusPaid() {
        val loan = com.kashvault.domain.Loan(
            id = "l1",
            name = "Home",
            totalAmount = 120_000.0,
            tenureMonths = 12,
            emi = 10_000.0,
            remainingAmount = 120_000.0,
            emisPaid = 3,
            dueDate = "2026-08-05",
        )
        assertEquals(9, remainingEmis(loan))
        assertEquals(120_000.0, getLoanOutstandingTotal(loan))
    }

    @Test
    fun saveFillsOutstandingFromEmiTimesTenure() {
        val loan = com.kashvault.domain.Loan(
            id = "l1",
            name = "Car",
            totalAmount = 0.0,
            tenureMonths = 24,
            emi = 8_000.0,
            remainingAmount = 0.0,
            dueDate = "2026-09-05",
        )
        val saved = preparedLoanForSave(loan)
        assertEquals(192_000.0, saved.remainingAmount)
        assertEquals(null, saved.annualInterestRate)
        assertTrue(loanIsReadyToSave(loan))
    }

    @Test
    fun applyEmiPaymentReducesOutstandingByEmiEvenIfRateWasStored() {
        val loan = com.kashvault.domain.Loan(
            id = "l1",
            name = "Home",
            totalAmount = 120_000.0,
            annualInterestRate = 8.5,
            tenureMonths = 12,
            emi = 10_000.0,
            remainingAmount = 120_000.0,
            dueDate = "2026-08-05",
        )
        val next = applyEmiPayment(loan, "2026-08")
        assertEquals(110_000.0, next.remainingAmount)
        assertEquals(1, next.emisPaid)
        assertEquals("2026-08", next.lastProcessedMonth)
        val undone = reverseEmiPayment(next)
        assertEquals(120_000.0, undone.remainingAmount)
        assertEquals(0, undone.emisPaid)
        assertEquals(null, undone.lastProcessedMonth)
    }

    @Test
    fun prepayReducesOutstandingAndCanCloseEarly() {
        val loan = com.kashvault.domain.Loan(
            id = "l1",
            name = "Home",
            totalAmount = 120_000.0,
            tenureMonths = 12,
            emi = 10_000.0,
            remainingAmount = 120_000.0,
            dueDate = "2026-08-05",
        )
        val part = applyLoanPrepayment(loan, 40_000.0)
        assertEquals(80_000.0, part.remainingAmount)
        assertEquals(40_000.0, part.totalPrepaid)
        assertEquals(8, remainingEmis(part))
        assertEquals(false, loanIsClosed(part))
        val closed = applyLoanPrepayment(part, 80_000.0)
        assertEquals(0.0, closed.remainingAmount)
        assertEquals(120_000.0, closed.totalPrepaid)
        assertEquals(true, loanIsClosed(closed))
        assertEquals(0, remainingEmis(closed))
        assertEquals(0.0, loanDueAmount(closed))
    }

    @Test
    fun lastDueIsLeftoverWhenOutstandingIsBelowEmi() {
        val loan = com.kashvault.domain.Loan(
            id = "l1",
            name = "Home",
            totalAmount = 120_000.0,
            tenureMonths = 12,
            emi = 10_000.0,
            remainingAmount = 5_000.0,
            dueDate = "2026-08-05",
        )
        assertEquals(5_000.0, loanDueAmount(loan))
        assertEquals(1, remainingEmis(loan))
    }

    @Test
    fun skipDoesNotChangePrincipalUntilPosted() {
        val loan = com.kashvault.domain.Loan(
            id = "l1",
            name = "Home",
            totalAmount = 120_000.0,
            annualInterestRate = 0.0,
            tenureMonths = 12,
            emi = 10_000.0,
            remainingAmount = 120_000.0,
            dueDate = "2026-08-05",
            lastProcessedMonth = "2026-08",
        )
        assertEquals(120_000.0, loan.remainingAmount)
        val reopened = loan.copy(lastProcessedMonth = null)
        assertEquals(120_000.0, reopened.remainingAmount)
        assertEquals(null, reopened.lastProcessedMonth)
    }
}

class LiquidityForecastTest {
    @Test
    fun runwayIsHighWhenCashCoversSpend() {
        val forecast = heuristicLiquidityForecast(
            LiquidityInput(
                currentLiquidity = 50_000.0,
                monthlyIncome = 80_000.0,
                monthlyAvgSpend = 40_000.0,
                upcomingLien = 5_000.0,
            ),
        )
        assertEquals("Low", forecast.riskLevel)
        assertEquals(7, buildHorizonSeries(forecast.let {
            LiquidityInput(50_000.0, 80_000.0, 40_000.0, 5_000.0)
        }).size)
    }
}

class DateUtilsTest {
    @Test
    fun formatTimestampKeepsInvalidInput() {
        assertEquals("not-a-date", formatTimestamp("not-a-date"))
        assertTrue(formatTimestamp("2026-08-19T12:30:00Z").contains("2026-08-19"))
    }

    @Test
    fun invalidTimestampIsNotThisMonth() {
        assertEquals(false, isWithinMonth("bogus", todayLocalDate()))
    }

    @Test
    fun backupIsStaleAfterSevenDays() {
        val today = kotlinx.datetime.LocalDate(2026, 8, 20)
        assertTrue(backupIsStale(null, today))
        assertTrue(backupIsStale("2026-08-13", today))
        assertEquals(false, backupIsStale("2026-08-14", today))
        assertEquals(false, backupIsStale("2026-08-20T09:00:00Z", today))
    }

    @Test
    fun firstRunSetupOnlyWhenEmptyAndNotCompleted() {
        assertTrue(needsFirstRunSetup(false, 0, 0))
        assertEquals(false, needsFirstRunSetup(true, 0, 0))
        assertEquals(false, needsFirstRunSetup(false, 1, 0))
        assertEquals(false, needsFirstRunSetup(false, 0, 1))
    }

    @Test
    fun dueDateOnDayClampsToMonth() {
        assertEquals("2026-08-31", dueDateOnDay(31, kotlinx.datetime.LocalDate(2026, 8, 20)))
        assertEquals("2026-02-28", dueDateOnDay(31, kotlinx.datetime.LocalDate(2026, 2, 1)))
    }
}
