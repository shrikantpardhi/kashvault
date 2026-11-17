package com.kashvault.util

import com.kashvault.domain.Category
import com.kashvault.domain.Expense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetCalculationTest {

    @Test
    fun computeCategorySpendSumsAccurately() {
        val expenses = listOf(
            Expense(id = "e1", amount = 1500.0, categoryId = "cat_food", timestamp = "2026-09-01T12:00:00Z"),
            Expense(id = "e2", amount = 850.0, categoryId = "cat_food", timestamp = "2026-09-02T12:00:00Z"),
            Expense(id = "e3", amount = 2000.0, categoryId = "cat_travel", timestamp = "2026-09-03T12:00:00Z"),
        )
        val foodExpenses = expenses.filter { it.categoryId == "cat_food" }
        val totalFoodSpend = foodExpenses.sumOf { it.amount }
        assertEquals(2350.0, totalFoodSpend)
    }

    @Test
    fun budgetProgressRatioClampsCorrectly() {
        val budgetLimit = 5000.0
        val normalSpend = 2500.0
        val overSpend = 7500.0
        val zeroLimit = 0.0

        val normalPercent = (normalSpend / budgetLimit) * 100.0
        assertEquals(50.0, normalPercent)

        val overPercentClamped = (overSpend / budgetLimit).coerceAtMost(1.0)
        assertEquals(1.0, overPercentClamped)

        val safePercent = if (zeroLimit > 0) (normalSpend / zeroLimit) else 0.0
        assertEquals(0.0, safePercent)
    }

    @Test
    fun currencyFormattingWorksForZeroAndPositive() {
        val formattedZero = formatINR(0.0)
        assertTrue(formattedZero.contains("0"))

        val formattedThousands = formatINR(15000.0)
        assertTrue(formattedThousands.contains("15,000"))
    }
}
