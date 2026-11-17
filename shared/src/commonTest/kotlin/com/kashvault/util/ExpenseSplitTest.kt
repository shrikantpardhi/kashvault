package com.kashvault.util

import com.kashvault.domain.Category
import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExpenseSplitTest {
    private val original = Expense(
        id = "e1",
        amount = 1_000.0,
        categoryId = "cat_grocery",
        timestamp = "2026-08-20T10:00:00Z",
        note = "UPI",
        type = "expense",
        sourceId = "b1",
    )

    @Test
    fun splitsSpendIntoTwoRowsThatSumToOriginal() {
        val parts = planExpenseSplit(
            original = original,
            firstCategoryId = "cat_grocery",
            firstAmount = 600.0,
            secondCategoryId = "cat_household",
            firstId = "s1",
            secondId = "s2",
        )
        assertNotNull(parts)
        assertEquals(600.0, parts.first.amount)
        assertEquals(400.0, parts.second.amount)
        assertEquals("cat_grocery", parts.first.categoryId)
        assertEquals("cat_household", parts.second.categoryId)
        assertEquals("b1", parts.first.sourceId)
        assertEquals("UPI", parts.second.note)
        assertEquals("2026-08-20T10:00:00Z", parts.second.timestamp)
    }

    @Test
    fun rejectsTransferAndInvalidAmounts() {
        val transfer = original.copy(type = Constants.TYPE_TRANSFER, destinationId = "c1")
        assertNull(
            planExpenseSplit(
                original = transfer,
                firstCategoryId = "cat_grocery",
                firstAmount = 500.0,
                secondCategoryId = "cat_household",
                firstId = "s1",
                secondId = "s2",
            ),
        )
        assertNull(
            planExpenseSplit(
                original = original,
                firstCategoryId = "cat_grocery",
                firstAmount = 1_000.0,
                secondCategoryId = "cat_household",
                firstId = "s1",
                secondId = "s2",
            ),
        )
    }
}

class BudgetPulseLineTest {
    @Test
    fun formatsTopCategoryPercent() {
        val summary = getCategoryBudgetUsage(
            categories = listOf(
                Category("cat_grocery", "Grocery", "🛒", "#0", "expense"),
                Category("cat_utils", "Utility bills", "💡", "#0", "expense"),
            ),
            budgets = listOf(
                com.kashvault.domain.Budget("cat_grocery", 9_000.0),
                com.kashvault.domain.Budget("cat_utils", 3_000.0),
            ),
            expenses = listOf(
                Expense("e1", 6_030.0, "cat_grocery", "2026-08-02T00:00:00Z", type = "expense"),
                Expense("e2", 500.0, "cat_utils", "2026-08-03T00:00:00Z", type = "expense"),
            ),
            today = kotlinx.datetime.LocalDate(2026, 8, 20),
        )
        assertEquals("Grocery 67% of budget", budgetPulseLine(summary))
    }

    @Test
    fun returnsNullWhenNoBudgets() {
        val summary = getCategoryBudgetUsage(
            categories = listOf(Category("cat_grocery", "Grocery", "🛒", "#0", "expense")),
            budgets = emptyList(),
            expenses = emptyList(),
        )
        assertNull(budgetPulseLine(summary))
    }
}
