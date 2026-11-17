package com.kashvault.util

import com.kashvault.domain.BorrowLend
import com.kashvault.domain.Expense
import com.kashvault.domain.EmergencyFund
import com.kashvault.domain.Investment
import com.kashvault.domain.Insurance
import com.kashvault.domain.Loan
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.Person
import com.kashvault.domain.RecurringBill
import com.kashvault.domain.SavingsGoal
import com.kashvault.domain.isIncome
import com.kashvault.domain.isSpend
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class NetWorthBreakdown(
    val liquidity: Double,
    val totalIncome: Double,
    val totalExpenses: Double,
    val investments: Double,
    val emergencyFund: Double,
    val savingsGoals: Double,
    val receivables: Double,
    val loanPrincipal: Double,
    val loanInterest: Double,
    val loanLiability: Double,
    val payables: Double,
    val netWorth: Double,
    val cardOutstanding: Double = 0.0,
)

data class NetWorthInput(
    val expenses: List<Expense>,
    val investments: List<Investment>,
    val emergencyFund: EmergencyFund,
    val savingsGoals: List<SavingsGoal>,
    val borrowLend: List<BorrowLend>,
    val loans: List<Loan>,
    val paymentSources: List<PaymentSource> = emptyList(),
)

data class PersonLendingNet(
    val person: String,
    val received: Double,
    val given: Double,
    val net: Double,
    val personId: String? = null,
)

data class LendingTotals(
    val toGive: Double,
    val totalReceived: Double,
    val overall: Double,
    val byPerson: List<PersonLendingNet>,
)

fun getFilteredExpenses(expenses: List<Expense>, timeframe: String, baseDate: LocalDate = todayLocalDate()): List<Expense> {
    return expenses.filter { expense ->
        if (timeframe == "all") return@filter true
        val instant = parseIsoTimestampOrNull(expense.timestamp) ?: return@filter false
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        when (timeframe) {
            "daily" -> dt == baseDate
            "weekly" -> {
                val weekStart = baseDate.minus(DatePeriod(days = baseDate.dayOfWeek.ordinal))
                val weekEnd = weekStart.plus(DatePeriod(days = 6))
                dt >= weekStart && dt <= weekEnd
            }
            "monthly" -> isWithinMonth(expense.timestamp, baseDate)
            "yearly" -> dt.year == baseDate.year
            else -> true
        }
    }
}

fun getPersonLendingNets(borrowLend: List<BorrowLend>, people: List<Person> = emptyList()): List<PersonLendingNet> {
    val groups = mutableMapOf<String, Pair<Double, Double>>()
    val names = mutableMapOf<String, String>()
    val ids = mutableMapOf<String, String?>()
    val peopleById = people.associateBy { it.id }

    for (item in borrowLend) {
        if (item.settled) continue
        val key = item.personId ?: item.person.trim().lowercase()
        val current = groups[key] ?: Pair(0.0, 0.0)
        val received = current.first
        val given = current.second
        names[key] = peopleById[item.personId]?.name ?: item.person
        ids[key] = item.personId ?: ids[key]
        if (item.type == "borrow") {
            groups[key] = Pair(received + item.amount, given)
        } else {
            groups[key] = Pair(received, given + item.amount)
        }
    }

    return groups.entries
        .map { (key, pair) ->
            PersonLendingNet(
                person = names[key] ?: key,
                received = pair.first,
                given = pair.second,
                net = pair.second - pair.first,
                personId = ids[key],
            )
        }
        .sortedBy { it.person }
}

fun getLendingTotals(borrowLend: List<BorrowLend>, people: List<Person> = emptyList()): LendingTotals {
    val byPerson = getPersonLendingNets(borrowLend, people)
    var toGive = 0.0
    var totalReceived = 0.0

    for (person in byPerson) {
        if (person.net > 0) totalReceived += person.net
        else if (person.net < 0) toGive += -person.net
    }

    return LendingTotals(toGive, totalReceived, totalReceived - toGive, byPerson)
}

fun getGlobalLiquidity(expenses: List<Expense>): Double {
    val totalIncome = expenses.filter { it.isIncome() }.sumOf { it.amount }
    val totalSpent = expenses.filter { it.isSpend() }.sumOf { it.amount }
    return totalIncome - totalSpent
}

fun getNetWorthBreakdown(input: NetWorthInput): NetWorthBreakdown {
    val totalIncome = input.expenses.filter { it.isIncome() }.sumOf { it.amount }
    val totalExpenses = input.expenses.filter { it.isSpend() }.sumOf { it.amount }
    val cards = cardOutstandingTotal(input.paymentSources, input.expenses)
    val liquidity = if (input.paymentSources.any { it.kind != com.kashvault.domain.Constants.SOURCE_CREDIT_CARD }) {
        availableCash(input.paymentSources, input.expenses)
    } else {
        totalIncome - totalExpenses
    }

    val investmentsTotal = input.investments.sumOf { getInvestmentPortfolioValue(it) }
    val emergencyFundBalance = input.emergencyFund.currentSaved
    val savingsGoalsTotal = input.savingsGoals.sumOf { it.current }
    val lending = getLendingTotals(input.borrowLend)
    val receivables = lending.totalReceived
    val payables = lending.toGive

    val loanPrincipal = input.loans.sumOf { getLoanOutstandingTotal(it) }
    val loanInterest = 0.0
    val loanLiability = loanPrincipal

    val netWorth = roundCurrency(
        liquidity + investmentsTotal + emergencyFundBalance + savingsGoalsTotal +
            receivables - loanLiability - payables - cards
    )

    return NetWorthBreakdown(
        liquidity = roundCurrency(liquidity),
        totalIncome = roundCurrency(totalIncome),
        totalExpenses = roundCurrency(totalExpenses),
        investments = roundCurrency(investmentsTotal),
        emergencyFund = roundCurrency(emergencyFundBalance),
        savingsGoals = roundCurrency(savingsGoalsTotal),
        receivables = roundCurrency(receivables),
        loanPrincipal = roundCurrency(loanPrincipal),
        loanInterest = roundCurrency(loanInterest),
        loanLiability = roundCurrency(loanLiability),
        payables = roundCurrency(payables),
        netWorth = netWorth,
        cardOutstanding = roundCurrency(cards),
    )
}

fun getNetWorth(input: NetWorthInput): Double = getNetWorthBreakdown(input).netWorth

fun getLienAmount(
    investments: List<Investment>,
    insurances: List<Insurance>,
    loans: List<Loan>,
    baseDate: LocalDate = todayLocalDate(),
    bills: List<RecurringBill> = emptyList(),
): Double {
    val currentMonth = "${baseDate.year}-${baseDate.monthNumber.toString().padStart(2, '0')}"
    val currentMonthIndex = baseDate.monthNumber - 1
    var totalLien = 0.0

    for (inv in investments) {
        val frequency = normalizeInvestmentFrequency(inv)
        if (
            inv.type == "SIP" &&
            inv.dueDate != null &&
            frequency != "OneTime" &&
            inv.lastProcessedMonth != currentMonth &&
            isFrequencyDueInMonth(frequency, inv.dueDate, currentMonthIndex)
        ) {
            totalLien += inv.amount
        }
    }

    for (ins in insurances) {
        if (
            ins.lastProcessedMonth != currentMonth &&
            isFrequencyDueInMonth(ins.frequency, ins.dueDate, currentMonthIndex)
        ) {
            totalLien += ins.premium
        }
    }

    for (loan in loans) {
        if (loan.lastProcessedMonth != currentMonth &&
            loanEmiShowsInCurrentMonth(loan.dueDate, loan.startDate, baseDate)
        ) {
            totalLien += loan.emi
        }
    }

    for (bill in bills) {
        if (bill.type == "income") continue
        if (
            bill.lastProcessedMonth != currentMonth &&
            isFrequencyDueInMonth(bill.frequency, bill.dueDate, currentMonthIndex)
        ) {
            totalLien += bill.amount
        }
    }

    return totalLien
}
