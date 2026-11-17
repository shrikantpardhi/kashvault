package com.kashvault.util

import com.kashvault.domain.Category
import com.kashvault.domain.EmergencyFund
import com.kashvault.domain.Expense
import com.kashvault.domain.Loan
import com.kashvault.domain.SavingsGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdvisorPromptsTest {

    private fun testBreakdown(
        liquidity: Double = 100000.0,
        investments: Double = 50000.0,
        emergencyFund: Double = 80000.0,
        savingsGoals: Double = 30000.0,
        loanLiability: Double = 0.0,
        netWorth: Double = 260000.0,
    ) = NetWorthBreakdown(
        liquidity = liquidity,
        totalIncome = 50000.0,
        totalExpenses = 20000.0,
        investments = investments,
        emergencyFund = emergencyFund,
        savingsGoals = savingsGoals,
        receivables = 0.0,
        loanPrincipal = loanLiability,
        loanInterest = 0.0,
        loanLiability = loanLiability,
        payables = 0.0,
        netWorth = netWorth,
    )

    private fun testLending() = LendingTotals(
        toGive = 0.0,
        totalReceived = 0.0,
        overall = 0.0,
        byPerson = emptyList(),
    )

    @Test
    fun predefinedPromptsAreNonEmptyAndDescriptive() {
        assertEquals(8, predefinedAdvisorAsks.size)
        predefinedAdvisorAsks.forEach { ask ->
            assertTrue(ask.title.isNotBlank(), "Title must not be blank")
            assertTrue(ask.description.isNotBlank(), "Description must not be blank")
            assertTrue(ask.prompt.isNotBlank(), "Prompt must not be blank")
            assertTrue(ask.icon.isNotBlank(), "Icon must not be blank")
        }
    }

    @Test
    fun predefinedPromptsHaveUniqueTitles() {
        val titles = predefinedAdvisorAsks.map { it.title }
        assertEquals(titles.distinct().size, titles.size, "All prompt titles must be distinct")
    }

    @Test
    fun heuristicAdvisorReplyGivesSpendSuggestions() {
        val expenses = listOf(
            Expense(id = "e1", amount = 5000.0, categoryId = "c1", timestamp = "2026-09-01T10:00:00Z", note = "Dinner"),
            Expense(id = "e2", amount = 2000.0, categoryId = "c2", timestamp = "2026-09-02T10:00:00Z", note = "Uber"),
        )
        val categories = listOf(
            Category("c1", "Food", "🍔", "#FF5722"),
            Category("c2", "Travel", "🚗", "#2196F3"),
        )
        val emergency = EmergencyFund(targetMonths = 6, monthlyRequirement = 25000.0, currentSaved = 80000.0)

        val reply = heuristicAdvisorReply(
            question = "How can I reduce expenses this month?",
            breakdown = testBreakdown(),
            lending = testLending(),
            emergency = emergency,
            loans = emptyList(),
            goals = emptyList(),
            expenses = expenses,
            categories = categories,
        )
        assertNotNull(reply)
        assertTrue(reply.contains("Food") || reply.contains("5,000") || reply.contains("spend") || reply.contains("Spend"))
    }

    @Test
    fun heuristicAdvisorReplyHandlesLoansAndEMIs() {
        val loans = listOf(
            Loan("l1", "Home Loan", 500000.0, 8.5, 36, emi = 15000.0, remainingAmount = 450000.0, dueDate = "05"),
        )
        val emergency = EmergencyFund(targetMonths = 6, monthlyRequirement = 25000.0, currentSaved = 80000.0)

        val reply = heuristicAdvisorReply(
            question = "Review my loans and debt payoff strategy",
            breakdown = testBreakdown(loanLiability = 450000.0, netWorth = -270000.0),
            lending = testLending(),
            emergency = emergency,
            loans = loans,
            goals = emptyList(),
            expenses = emptyList(),
            categories = emptyList(),
        )
        assertNotNull(reply)
        assertTrue(reply.contains("Home Loan") || reply.contains("4,50,000") || reply.contains("loan") || reply.contains("EMI"))
    }

    @Test
    fun heuristicAdvisorReplyHandlesEmergencyFund() {
        val emergency = EmergencyFund(targetMonths = 6, monthlyRequirement = 30000.0, currentSaved = 90000.0)

        val reply = heuristicAdvisorReply(
            question = "Is my emergency fund sufficient?",
            breakdown = testBreakdown(liquidity = 90000.0, emergencyFund = 90000.0, netWorth = 90000.0),
            lending = testLending(),
            emergency = emergency,
            loans = emptyList(),
            goals = emptyList(),
            expenses = emptyList(),
            categories = emptyList(),
        )
        assertNotNull(reply)
        assertTrue(reply.contains("Emergency") || reply.contains("emergency") || reply.contains("months") || reply.contains("fund"))
    }

    @Test
    fun heuristicAdvisorReplyHandlesSavingsGoals() {
        val goals = listOf(
            SavingsGoal("g1", "Vacation", 100000.0, 45000.0, completed = false),
        )
        val emergency = EmergencyFund(targetMonths = 6, monthlyRequirement = 25000.0, currentSaved = 80000.0)

        val reply = heuristicAdvisorReply(
            question = "Am I on track for my savings goals?",
            breakdown = testBreakdown(savingsGoals = 45000.0, netWorth = 225000.0),
            lending = testLending(),
            emergency = emergency,
            loans = emptyList(),
            goals = goals,
            expenses = emptyList(),
            categories = emptyList(),
        )
        assertNotNull(reply)
        assertTrue(reply.contains("Vacation") || reply.contains("goals") || reply.contains("saved") || reply.contains("target"))
    }

    @Test
    fun formatOneDecimalWorksCorrectly() {
        assertEquals("3.5", formatOneDecimal(3.54))
        assertEquals("0.0", formatOneDecimal(0.0))
        assertEquals("10.0", formatOneDecimal(10.02))
    }
}
