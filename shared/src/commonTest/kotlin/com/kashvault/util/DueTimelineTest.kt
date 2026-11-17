package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.EmergencyFund
import com.kashvault.domain.Expense
import com.kashvault.domain.Insurance
import com.kashvault.domain.Investment
import com.kashvault.domain.Loan
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.RecurringBill
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DueTimelineTest {
    private val today = LocalDate(2026, 8, 20)

    @Test
    fun monthlyEmiUsesNextMonthWhenDayHasPassed() {
        val due = nextDueDate("2026-08-05", "Monthly", lastProcessedMonth = null, today = today)
        assertEquals(LocalDate(2026, 9, 5), due)
    }

    @Test
    fun firstEmiNextMonthShowsEvenWhenBeyondFortyFiveDays() {
        val today = LocalDate(2026, 8, 1)
        val loan = Loan(
            id = "loan1",
            name = "Home",
            totalAmount = 1_000_000.0,
            emi = 20_000.0,
            remainingAmount = 800_000.0,
            dueDate = "2026-08-30",
            startDate = "2026-09-01",
        )
        assertEquals(LocalDate(2026, 9, 30), loanFirstEmiDate(loan.dueDate, loan.startDate))
        assertTrue(loanEmiShowsInCurrentMonth(loan.dueDate, loan.startDate, today))
        val timeline = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = listOf(loan),
            today = today,
            horizonDays = 45,
        )
        assertEquals(1, timeline.size)
        assertEquals(LocalDate(2026, 9, 30), timeline.first().dueOn)
    }

    @Test
    fun firstEmiNextMonthShowsOnCurrentMonthTimeline() {
        val loan = Loan(
            id = "loan1",
            name = "Home",
            totalAmount = 1_000_000.0,
            emi = 20_000.0,
            remainingAmount = 800_000.0,
            dueDate = "2026-08-05",
            startDate = "2026-09-01",
        )
        assertEquals(LocalDate(2026, 9, 5), loanFirstEmiDate(loan.dueDate, loan.startDate))
        val auto = collectDuePostCandidates(
            investments = emptyList(),
            insurances = emptyList(),
            loans = listOf(loan),
            today = today,
            includeFuture = false,
        )
        assertTrue(auto.none { it.kind == "emi" })
        val timeline = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = listOf(loan),
            today = today,
        )
        assertEquals(1, timeline.size)
        assertEquals(LocalDate(2026, 9, 5), timeline.first().dueOn)
        assertTrue(timeline.first().daysUntil > 0)
        assertTrue(timeline.first().canMarkPaid)
    }

    @Test
    fun closedLoanLeavesTheDueTimeline() {
        val loan = Loan(
            id = "loan1",
            name = "Home",
            totalAmount = 1_000_000.0,
            emi = 20_000.0,
            remainingAmount = 0.0,
            dueDate = "2026-08-05",
        )
        val timeline = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = listOf(loan),
            today = today,
        )
        assertTrue(timeline.none { it.kind == "emi" })
    }

    @Test
    fun leftoverOutstandingBecomesTheDueAmount() {
        val loan = Loan(
            id = "loan1",
            name = "Home",
            totalAmount = 1_000_000.0,
            emi = 20_000.0,
            remainingAmount = 7_500.0,
            dueDate = "2026-08-28",
        )
        val timeline = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = listOf(loan),
            today = today,
        )
        assertEquals(1, timeline.size)
        assertEquals(7_500.0, timeline.first().amount)
    }

    @Test
    fun dueDateInNextMonthIsFirstEmiEvenWithoutStartDate() {
        val loan = Loan(
            id = "loan1",
            name = "Car",
            totalAmount = 500_000.0,
            emi = 12_000.0,
            remainingAmount = 500_000.0,
            dueDate = "2026-09-05",
        )
        val timeline = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = listOf(loan),
            today = today,
        )
        assertEquals(1, timeline.size)
        assertEquals(LocalDate(2026, 9, 5), timeline.first().dueOn)
        assertNull(
            openDueOn(
                dueDate = loan.dueDate,
                frequency = "Monthly",
                lastProcessedMonth = null,
                today = today,
                includeFuture = false,
                notBefore = loanFirstEmiDate(loan.dueDate, loan.startDate),
            ),
        )
    }

    @Test
    fun processedThisMonthSkipsAhead() {
        val due = nextDueDate("2026-08-25", "Monthly", lastProcessedMonth = "2026-08", today = today)
        assertEquals(LocalDate(2026, 9, 25), due)
    }

    @Test
    fun timelineIncludesUpcomingSipAndEmi() {
        val items = buildDueTimeline(
            investments = listOf(
                Investment(
                    id = "sip1",
                    name = "Nifty SIP",
                    amount = 5000.0,
                    type = "SIP",
                    frequency = "Monthly",
                    timestamp = "2026-01-01T00:00:00Z",
                    dueDate = "2026-08-22",
                ),
            ),
            insurances = listOf(
                Insurance(
                    id = "ins1",
                    name = "Health",
                    premium = 1200.0,
                    frequency = "Monthly",
                    dueDate = "2026-08-21",
                ),
            ),
            loans = listOf(
                Loan(
                    id = "loan1",
                    name = "Home",
                    totalAmount = 1_000_000.0,
                    emi = 20_000.0,
                    remainingAmount = 800_000.0,
                    dueDate = "2026-08-28",
                ),
            ),
            today = today,
        )
        assertEquals(3, items.size)
        assertEquals("Health", items.first().title)
        assertTrue(items.any { it.kind == "emi" })
    }

    @Test
    fun emergencyAndGoalProgress() {
        assertEquals(50.0, emergencyFundProgress(6, 10_000.0, 30_000.0))
        assertEquals(25.0, goalsProgress(250.0, 1_000.0))
        assertEquals("SP", personInitials("Shrikant Pardhi"))
    }

    @Test
    fun autoDueCandidatesSkipFutureAndKeepOverdue() {
        val today = LocalDate(2026, 8, 20)
        val futureSip = Investment(
            id = "sip1",
            name = "Nifty",
            amount = 5000.0,
            type = "SIP",
            frequency = "Monthly",
            timestamp = "2026-01-01T00:00:00Z",
            dueDate = "2026-08-25",
            sourceId = "b1",
        )
        val overdueEmi = Loan(
            id = "loan1",
            name = "Home",
            totalAmount = 1_000_000.0,
            emi = 20_000.0,
            remainingAmount = 800_000.0,
            dueDate = "2026-08-05",
            sourceId = "b1",
        )
        val auto = collectDuePostCandidates(
            investments = listOf(futureSip),
            insurances = emptyList(),
            loans = listOf(overdueEmi),
            today = today,
            includeFuture = false,
        )
        assertEquals(1, auto.size)
        assertEquals("emi", auto.first().kind)
        val manual = collectDuePostCandidates(
            investments = listOf(futureSip),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            includeFuture = true,
        )
        assertEquals(1, manual.size)
        assertEquals("sip", manual.first().kind)
    }

    @Test
    fun recurringBillAndSalaryRespectDueDate() {
        val rent = RecurringBill(
            id = "rent1",
            name = "Home Rent",
            amount = 15_000.0,
            categoryId = "cat_rent",
            frequency = "Monthly",
            dueDate = "2026-08-25",
            sourceId = "b1",
            type = "expense",
        )
        val salary = RecurringBill(
            id = "sal1",
            name = "Salary",
            amount = 80_000.0,
            categoryId = "cat_salary",
            frequency = "Monthly",
            dueDate = "2026-08-01",
            sourceId = "b1",
            type = "income",
        )
        val auto = collectDuePostCandidates(
            investments = emptyList(),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            includeFuture = false,
            bills = listOf(rent, salary),
        )
        assertEquals(1, auto.size)
        assertEquals(DUE_KIND_SALARY, auto.first().kind)
        assertEquals("income", auto.first().expenseType)
        val manual = collectDuePostCandidates(
            investments = emptyList(),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            includeFuture = true,
            bills = listOf(rent, salary),
        )
        assertEquals(2, manual.size)
        assertTrue(manual.any { it.kind == DUE_KIND_BILL && it.title == "Home Rent" })
    }

    @Test
    fun cardBillIsNotACalendarDue() {
        val bank = PaymentSource("b1", "HDFC", Constants.SOURCE_BANK, openingBalance = 20_000.0)
        val card = PaymentSource(
            id = "c1",
            name = "Ace",
            kind = Constants.SOURCE_CREDIT_CARD,
            openingBalance = 0.0,
            statementDueDate = "2026-08-15",
            payFromSourceId = "b1",
        )
        val expenses = listOf(
            Expense("e1", 3_500.0, "cat_food", "2026-08-02T00:00:00Z", type = "expense", sourceId = "c1"),
        )
        val auto = collectDuePostCandidates(
            investments = emptyList(),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            includeFuture = false,
            sources = listOf(bank, card),
            expenses = expenses,
        )
        assertTrue(auto.none { it.kind == DUE_KIND_CARD })
        val timeline = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            sources = listOf(bank, card),
            expenses = expenses,
        )
        assertTrue(timeline.none { it.kind == DUE_KIND_CARD })
    }

    @Test
    fun cardBillWithZeroOutstandingIsNotDue() {
        val card = PaymentSource(
            id = "c1",
            name = "Ace",
            kind = Constants.SOURCE_CREDIT_CARD,
            openingBalance = 0.0,
            statementDueDate = "2026-08-15",
            payFromSourceId = "b1",
        )
        val auto = collectDuePostCandidates(
            investments = emptyList(),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            includeFuture = false,
            sources = listOf(card),
        )
        assertTrue(auto.isEmpty())
    }

    @Test
    fun paidThisMonthStaysOnTimelineForUndo() {
        val items = buildDueTimeline(
            investments = emptyList(),
            insurances = emptyList(),
            loans = emptyList(),
            today = today,
            bills = listOf(
                RecurringBill(
                    id = "rent1",
                    name = "Home Rent",
                    amount = 15_000.0,
                    categoryId = "cat_rent",
                    frequency = "Monthly",
                    dueDate = "2026-08-05",
                    lastProcessedMonth = "2026-08",
                    type = "expense",
                ),
            ),
        )
        assertEquals(1, items.size)
        assertTrue(items.first().paidThisMonth)
        assertTrue(items.first().canUndo)
        assertFalse(items.first().canMarkPaid)
    }

    @Test
    fun matchInboxPrefersSameSource() {
        val rent = DuePostCandidate(
            kind = DUE_KIND_BILL,
            entityId = "rent1",
            title = "Home Rent",
            amount = 15_000.0,
            dueOn = LocalDate(2026, 8, 5),
            itemSourceId = "b1",
            categoryId = "cat_rent",
        )
        val sip = DuePostCandidate(
            kind = DUE_KIND_SIP,
            entityId = "sip1",
            title = "Nifty",
            amount = 15_000.0,
            dueOn = LocalDate(2026, 8, 7),
            itemSourceId = "b2",
            categoryId = "cat_wealth",
        )
        val match = matchInboxToDue(15_000.0, "expense", "b2", listOf(rent, sip))
        assertEquals("sip1", match?.entityId)
        val other = matchInboxToDue(15_000.0, "expense", "b1", listOf(rent, sip))
        assertEquals("rent1", other?.entityId)
        assertNull(matchInboxToDue(15_000.0, "income", "b1", listOf(rent, sip)))
    }

    @Test
    fun matchInboxMatchesSalaryIncome() {
        val salary = DuePostCandidate(
            kind = DUE_KIND_SALARY,
            entityId = "sal1",
            title = "Salary",
            amount = 80_000.0,
            dueOn = LocalDate(2026, 8, 1),
            itemSourceId = "b1",
            categoryId = "cat_salary",
            expenseType = "income",
        )
        assertEquals("sal1", matchInboxToDue(80_000.0, "income", "b1", listOf(salary))?.entityId)
        assertNull(matchInboxToDue(80_000.0, "expense", "b1", listOf(salary)))
    }
}

class DateStampTest {
    @Test
    fun parsesAndFormatsDates() {
        val date = parseLocalDateOrNull("2026-08-20T12:30:00Z")
        assertEquals(LocalDate(2026, 8, 20), date)
        assertEquals("20 Aug 2026", formatDisplayDate(LocalDate(2026, 8, 20)))
        assertEquals("05 Aug 2026", formatDisplayDate(LocalDate(2026, 8, 5)))
        assertEquals("Sep 2026", formatMonthYear(LocalDate(2026, 9, 5)))
        assertEquals("Good evening", greetingForHour(19))
    }
}

class AdvisorHeuristicTest {
    @Test
    fun localReplyMentionsCashWhenAskedAboutRunway() {
        val reply = heuristicAdvisorReply(
            question = "How many months of cash do I have left?",
            breakdown = NetWorthBreakdown(
                liquidity = 50_000.0,
                totalIncome = 80_000.0,
                totalExpenses = 30_000.0,
                investments = 10_000.0,
                emergencyFund = 20_000.0,
                savingsGoals = 5_000.0,
                receivables = 0.0,
                loanPrincipal = 0.0,
                loanInterest = 0.0,
                loanLiability = 0.0,
                payables = 0.0,
                netWorth = 85_000.0,
            ),
            lending = LendingTotals(0.0, 0.0, 0.0, emptyList()),
            emergency = EmergencyFund(6, 10_000.0, 20_000.0),
            loans = emptyList(),
            goals = emptyList(),
            expenses = emptyList(),
            categories = emptyList(),
        )
        assertTrue(reply.contains("Cash on hand"))
        assertTrue(reply.contains("Local read"))
        assertTrue(reply.contains("add a Gemini API key"))
    }

    @Test
    fun localReplyDoesNotAskForKeyWhenOneExists() {
        val reply = heuristicAdvisorReply(
            question = "How many months of cash do I have left?",
            breakdown = NetWorthBreakdown(
                liquidity = 50_000.0,
                totalIncome = 80_000.0,
                totalExpenses = 30_000.0,
                investments = 10_000.0,
                emergencyFund = 20_000.0,
                savingsGoals = 5_000.0,
                receivables = 0.0,
                loanPrincipal = 0.0,
                loanInterest = 0.0,
                loanLiability = 0.0,
                payables = 0.0,
                netWorth = 85_000.0,
            ),
            lending = LendingTotals(0.0, 0.0, 0.0, emptyList()),
            emergency = EmergencyFund(6, 10_000.0, 20_000.0),
            loans = emptyList(),
            goals = emptyList(),
            expenses = emptyList(),
            categories = emptyList(),
            hasGeminiKey = true,
        )
        assertTrue(reply.contains("Local read from this vault"))
        assertFalse(reply.contains("add a Gemini API key"))
    }
}
