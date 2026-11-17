package com.kashvault.util

import com.kashvault.domain.Budget
import com.kashvault.domain.Category
import com.kashvault.domain.Expense
import com.kashvault.domain.isSpend
import kotlinx.datetime.LocalDate

const val BUDGET_ALERT_THRESHOLD = 80.0

data class CategoryBudgetUsage(
    val categoryId: String,
    val name: String,
    val icon: String,
    val color: String,
    val spent: Double,
    val limit: Double,
    val percent: Double,
    val remaining: Double,
    val isOverLimit: Boolean,
    val isAlert: Boolean,
)

data class BudgetUsageSummary(
    val categories: List<CategoryBudgetUsage>,
    val alertCategories: List<CategoryBudgetUsage>,
    val totalSpent: Double,
    val totalLimit: Double,
    val overallPercent: Double,
    val anyOver: Boolean,
)

fun getCategoryBudgetUsage(
    categories: List<Category>,
    budgets: List<Budget>,
    expenses: List<Expense>,
    today: LocalDate = todayLocalDate(),
): BudgetUsageSummary {
    val monthly = getFilteredExpenses(expenses, "monthly", today).filter { it.isSpend() }
    val totals = mutableMapOf<String, Double>()
    monthly.forEach { expense ->
        totals[expense.categoryId] = (totals[expense.categoryId] ?: 0.0) + expense.amount
    }

    val budgetMap = budgets.filter { it.limit > 0 }.associate { it.categoryId to it.limit }

    val usage = categories
        .filter { it.type != "income" && budgetMap.containsKey(it.id) }
        .map { category ->
            val limit = budgetMap[category.id] ?: 0.0
            val spent = totals[category.id] ?: 0.0
            val percent = if (limit > 0) (spent / limit) * 100 else 0.0
            CategoryBudgetUsage(
                categoryId = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                spent = spent,
                limit = limit,
                percent = percent,
                remaining = maxOf(limit - spent, 0.0),
                isOverLimit = spent > limit,
                isAlert = percent >= BUDGET_ALERT_THRESHOLD,
            )
        }
        .sortedByDescending { it.percent }

    val totalLimit = usage.sumOf { it.limit }
    val totalSpent = usage.sumOf { it.spent }

    return BudgetUsageSummary(
        categories = usage,
        alertCategories = usage.filter { it.isAlert },
        totalSpent = totalSpent,
        totalLimit = totalLimit,
        overallPercent = if (totalLimit > 0) minOf((totalSpent / totalLimit) * 100, 999.0) else 0.0,
        anyOver = usage.any { it.isOverLimit },
    )
}

fun budgetPulseLine(summary: BudgetUsageSummary): String? {
    val top = summary.categories.maxByOrNull { it.percent } ?: return null
    val pct = top.percent.toInt().coerceAtLeast(0)
    return if (top.isOverLimit) "${top.name} over budget ($pct%)" else "${top.name} $pct% of budget"
}
