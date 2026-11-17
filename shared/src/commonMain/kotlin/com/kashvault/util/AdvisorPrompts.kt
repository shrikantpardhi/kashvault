package com.kashvault.util

import com.kashvault.domain.Category
import com.kashvault.domain.EmergencyFund
import com.kashvault.domain.Expense
import com.kashvault.domain.Loan
import com.kashvault.domain.SavingsGoal

data class AdvisorChatMessage(
    val role: String,
    val text: String,
)

data class AdvisorPrompt(
    val title: String,
    val description: String,
    val prompt: String,
    val icon: String = "⚡",
)

fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    val whole = rounded.toLong()
    val tenth = kotlin.math.round((rounded - whole) * 10.0).toLong()
    return "$whole.$tenth"
}

val predefinedAdvisorAsks = listOf(
    AdvisorPrompt(
        title = "Optimize monthly burn",
        description = "Identifies heavy spending leaks & gives targeted 10% trim tactics",
        prompt = "Analyze my top spending categories this month and recommend actionable ways to cut expenses by 10%.",
        icon = "📉",
    ),
    AdvisorPrompt(
        title = "Cash runway & liquid safety",
        description = "Calculates days of cash remaining against active burn & upcoming dues",
        prompt = "How many months of liquid runway do I have left at my current monthly burn rate, and are my cash buffers safe?",
        icon = "🛡️",
    ),
    AdvisorPrompt(
        title = "Loan payoff & EMI strategy",
        description = "Evaluates debt snowball vs avalanche payoff with prepayment advice",
        prompt = "Review my active loans, EMIs, and liabilities. What is the fastest and most cost-effective prepayment strategy?",
        icon = "💳",
    ),
    AdvisorPrompt(
        title = "Emergency fund health check",
        description = "Assesses your 6-month safety net and monthly coverage progress",
        prompt = "Is my emergency fund sufficient for 6 months of living expenses, and what should I prioritize next?",
        icon = "🚨",
    ),
    AdvisorPrompt(
        title = "Surplus cash allocation",
        description = "Recommends whether to save, invest, or prepay debt with extra funds",
        prompt = "Where should I allocate my surplus cash this month between savings goals, debt prepayment, and emergency reserves?",
        icon = "💰",
    ),
    AdvisorPrompt(
        title = "Savings goals pace & projection",
        description = "Audits target dates, funding gaps, and monthly contribution pace",
        prompt = "Am I on track to meet my savings goals on time? How much should I contribute monthly to bridge the gap?",
        icon = "🎯",
    ),
    AdvisorPrompt(
        title = "Lending & debt settlement audit",
        description = "Summarizes net receivables, borrowed sums, and open person ledgers",
        prompt = "Summarize everyone who owes me money and everyone I owe, and give me a net settlement breakdown.",
        icon = "🤝",
    ),
    AdvisorPrompt(
        title = "Full financial vault diagnosis",
        description = "Comprehensive audit of net worth, cash flow, debt-to-income, and liquidity",
        prompt = "Give me a comprehensive 360° financial diagnosis covering my net worth, liquidity, debt ratio, and cash flow health.",
        icon = "📊",
    ),
)

fun heuristicAdvisorReply(
    question: String,
    breakdown: NetWorthBreakdown,
    lending: LendingTotals,
    emergency: EmergencyFund,
    loans: List<Loan>,
    goals: List<SavingsGoal>,
    expenses: List<Expense>,
    categories: List<Category>,
    hasGeminiKey: Boolean = false,
): String {
    val q = question.lowercase()
    val monthlyFiltered = getFilteredExpenses(expenses, "monthly")
    val monthly = if (monthlyFiltered.isEmpty() && expenses.isNotEmpty()) expenses else monthlyFiltered
    val spent = monthly.filter { it.type != "income" }
    val income = monthly.filter { it.type == "income" }.sumOf { it.amount }
    val spendTotal = spent.sumOf { it.amount }
    val byCategory = spent.groupBy { it.categoryId }
        .map { (id, rows) ->
            val name = categories.find { it.id == id }?.let { "${it.icon} ${it.name}" } ?: "Other"
            name to rows.sumOf { it.amount }
        }
        .sortedByDescending { it.second }

    val emergencyTarget = emergency.monthlyRequirement * emergency.targetMonths
    val emergencyMonths = if (emergency.monthlyRequirement > 0) {
        emergency.currentSaved / emergency.monthlyRequirement
    } else 0.0

    fun snapshot(): String = buildString {
        appendLine("Vault snapshot")
        appendLine("• Available cash / liquidity: ${formatINR(breakdown.liquidity)}")
        appendLine("• Net worth: ${formatINR(breakdown.netWorth)}")
        appendLine("• Invested: ${formatINR(breakdown.investments)}")
        appendLine("• Goals saved: ${formatINR(breakdown.savingsGoals)}")
        appendLine("• Emergency fund: ${formatINR(emergency.currentSaved)}")
        appendLine("• Loan liability: ${formatINR(breakdown.loanLiability)}")
        appendLine("• Lending net: ${formatINR(lending.overall)}")
        appendLine("• This month: +${formatINR(income)} in · −${formatINR(spendTotal)} out")
    }

    val body = when {
        listOf("expense", "spend", "reduce", "cut").any { it in q } || "top spend" in q -> {
            val top = byCategory.take(5).joinToString("\n") { "• ${it.first}: ${formatINR(it.second)}" }
            val lead = byCategory.firstOrNull()
            buildString {
                appendLine("This month you spent ${formatINR(spendTotal)} against ${formatINR(income)} income.")
                if (lead != null) appendLine("Largest bucket: ${lead.first} at ${formatINR(lead.second)}.")
                if (top.isNotEmpty()) {
                    appendLine()
                    appendLine("Top categories")
                    appendLine(top)
                }
                appendLine()
                appendLine("Try a 10% cut on the top two categories, and park the difference into emergency or a named goal.")
            }
        }
        listOf("loan", "emi", "debt").any { it in q } -> {
            buildString {
                appendLine("Loan book: ${loans.size} loan(s), outstanding ${formatINR(breakdown.loanLiability)}.")
                loans.take(6).forEach { loan ->
                    val left = remainingEmis(loan)
                    val tenure = loan.tenureMonths
                    val months = if (tenure != null && tenure > 0) "$left of $tenure mo left" else "$left mo left"
                    appendLine("• ${loan.name}: EMI ${formatINR(loan.emi)}, $months, outstanding ${formatINR(getLoanOutstandingTotal(loan))}")
                }
                appendLine()
                appendLine("If cash after lien is healthy, prepay the loan with the fewest months left. Keep one EMI buffer in cash.")
            }
        }
        listOf("saving", "goal").any { it in q } -> {
            buildString {
                if (goals.isEmpty()) {
                    appendLine("No savings goals yet. Add one on Wealth so progress is visible.")
                } else {
                    goals.forEach { goal ->
                        val pct = goalsProgress(goal.current, goal.target).toInt()
                        appendLine("• ${goal.name}: ${formatINR(goal.current)} / ${formatINR(goal.target)} ($pct%)")
                    }
                }
                appendLine()
                appendLine("Aim to move surplus (${formatINR((income - spendTotal).coerceAtLeast(0.0))} this month) into the furthest-behind goal.")
            }
        }
        listOf("lend", "borrow", "owe").any { it in q } -> {
            buildString {
                appendLine("To receive ${formatINR(lending.totalReceived)} · to give ${formatINR(lending.toGive)}.")
                lending.byPerson.take(8).forEach { person ->
                    val direction = when {
                        person.net > 0 -> "owes you ${formatINR(person.net)}"
                        person.net < 0 -> "you owe ${formatINR(-person.net)}"
                        else -> "settled"
                    }
                    appendLine("• ${person.person}: $direction")
                }
                if (lending.byPerson.isEmpty()) appendLine("No open person logs.")
            }
        }
        "emergency" in q -> {
            buildString {
                appendLine("Emergency fund ${formatINR(emergency.currentSaved)}.")
                if (emergencyTarget > 0) {
                    appendLine("Target ${emergency.targetMonths} months (${formatINR(emergencyTarget)}).")
                    appendLine("Covered ≈ ${formatOneDecimal(emergencyMonths)} month(s).")
                } else {
                    appendLine("Set a monthly requirement on Goals to track months of cover.")
                }
                if (emergencyMonths < emergency.targetMonths) {
                    appendLine("Priority: fill this before extra investing.")
                } else {
                    appendLine("Cover looks complete. Direct surplus to goals or prepayments.")
                }
            }
        }
        listOf("cash", "liquid", "runway", "extra").any { it in q } -> {
            val spend = if (spendTotal > 0) spendTotal else breakdown.totalExpenses / 12.0
            val runway = if (spend > 0) (breakdown.liquidity / spend) else 12.0
            buildString {
                appendLine("Cash on hand ${formatINR(breakdown.liquidity)}.")
                appendLine("At this month’s spend, that is about ${formatOneDecimal(runway)} month(s) of runway.")
                appendLine("Upcoming EMIs/SIPs sit as lien against that cash on Home.")
                if (breakdown.liquidity > spend * 3 && emergencyMonths >= emergency.targetMonths && emergency.targetMonths > 0) {
                    appendLine("You can put extra cash into goals or the loan with the fewest months left.")
                } else {
                    appendLine("Hold cash until emergency cover and the next 30 days of dues are comfortable.")
                }
            }
        }
        else -> snapshot()
    }

    return buildString {
        if (hasGeminiKey) {
            appendLine("Local read from this vault.")
        } else {
            appendLine("Local read — add a Gemini API key in Profile for a richer answer.")
        }
        appendLine()
        append(body.trim())
        appendLine()
        appendLine()
        append(snapshot().trim())
    }
}
