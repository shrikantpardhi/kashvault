package com.kashvault.util

import com.kashvault.domain.Investment
import com.kashvault.domain.Loan
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.ceil
import kotlin.math.min

fun loanFirstEmiDate(dueDate: String, startDate: String?): LocalDate? {
    val due = parseLocalDateOrNull(dueDate)
    val start = parseLocalDateOrNull(startDate)
    if (due == null) return start
    if (start == null) return due
    val firstInStartMonth = LocalDate(
        start.year,
        start.monthNumber,
        clampDayOfMonth(start.year, start.monthNumber, due.dayOfMonth),
    )
    return maxOf(due, firstInStartMonth)
}

fun loanEmiShowsInCurrentMonth(dueDate: String, startDate: String?, today: LocalDate): Boolean {
    val first = loanFirstEmiDate(dueDate, startDate) ?: return true
    val firstMonth = monthKey(first)
    return firstMonth <= monthKey(today) || firstMonth == monthKey(today.plus(DatePeriod(months = 1)))
}

fun normalizeLoan(loan: Loan): Loan {
    val tenureMonths = loan.tenureMonths?.takeIf { it > 0 }
    return loan.copy(
        tenureMonths = tenureMonths,
        emi = loan.emi.coerceAtLeast(0.0),
        remainingAmount = loan.remainingAmount.coerceAtLeast(0.0),
        totalPrepaid = loan.totalPrepaid ?: 0.0,
        emisPaid = loan.emisPaid ?: 0,
    )
}

fun remainingEmis(loan: Loan): Int {
    val normalized = normalizeLoan(loan)
    if (normalized.remainingAmount <= 0) return 0
    val paid = normalized.emisPaid ?: 0
    val tenure = normalized.tenureMonths
    val byBalance = if (normalized.emi > 0) {
        ceil(normalized.remainingAmount / normalized.emi).toInt()
    } else {
        null
    }
    val byTenure = tenure?.let { (it - paid).coerceAtLeast(0) }
    return when {
        byBalance != null && byTenure != null -> min(byBalance, byTenure)
        byBalance != null -> byBalance
        byTenure != null -> byTenure
        else -> 0
    }
}

fun getLoanOutstandingTotal(loan: Loan): Double {
    return roundCurrency(normalizeLoan(loan).remainingAmount)
}

fun loanIsClosed(loan: Loan): Boolean = getLoanOutstandingTotal(loan) <= 0.0

fun loanDueAmount(loan: Loan): Double {
    val outstanding = getLoanOutstandingTotal(loan)
    if (outstanding <= 0.0) return 0.0
    val emi = normalizeLoan(loan).emi
    if (emi <= 0.0) return outstanding
    return roundCurrency(min(emi, outstanding))
}

fun applyLoanPrepayment(loan: Loan, amount: Double): Loan {
    val normalized = normalizeLoan(loan)
    val payment = roundCurrency(min(amount, normalized.remainingAmount).coerceAtLeast(0.0))
    return normalized.copy(
        remainingAmount = roundCurrency((normalized.remainingAmount - payment).coerceAtLeast(0.0)),
        totalPrepaid = roundCurrency((normalized.totalPrepaid ?: 0.0) + payment),
        remainingInterest = null,
        outstandingTotal = null,
    )
}

fun applyEmiPayment(loan: Loan, processedMonth: String): Loan {
    val normalized = normalizeLoan(loan)
    val payment = roundCurrency(min(normalized.emi, normalized.remainingAmount).coerceAtLeast(0.0))
    return normalized.copy(
        remainingAmount = roundCurrency((normalized.remainingAmount - payment).coerceAtLeast(0.0)),
        remainingInterest = null,
        outstandingTotal = null,
        emisPaid = (normalized.emisPaid ?: 0) + 1,
        lastProcessedMonth = processedMonth,
        principalPerEmi = payment,
        interestPerEmi = 0.0,
    )
}

fun reverseEmiPayment(loan: Loan): Loan {
    val normalized = normalizeLoan(loan)
    val payment = normalized.principalPerEmi ?: normalized.emi
    return normalized.copy(
        remainingAmount = roundCurrency(normalized.remainingAmount + payment),
        remainingInterest = null,
        outstandingTotal = null,
        emisPaid = ((normalized.emisPaid ?: 1) - 1).coerceAtLeast(0),
        lastProcessedMonth = null,
    )
}

fun preparedLoanForSave(loan: Loan): Loan {
    val normalized = normalizeLoan(loan)
    val tenure = normalized.tenureMonths
    val paid = normalized.emisPaid ?: 0
    val remaining = when {
        normalized.remainingAmount > 0 -> normalized.remainingAmount
        tenure != null && normalized.emi > 0 -> roundCurrency(normalized.emi * (tenure - paid).coerceAtLeast(0))
        else -> normalized.remainingAmount
    }
    return normalized.copy(
        totalAmount = if (normalized.totalAmount > 0) normalized.totalAmount else remaining,
        remainingAmount = remaining,
        annualInterestRate = null,
        remainingInterest = null,
        outstandingTotal = remaining,
    )
}

fun loanIsReadyToSave(loan: Loan): Boolean {
    if (loan.name.isBlank() || loan.emi <= 0) return false
    return loan.remainingAmount > 0 || (loan.tenureMonths ?: 0) > 0
}

fun getInvestmentPortfolioValue(investment: Investment): Double {
    val invested = investment.investedAmount
    if (invested != null && invested > 0.0) return invested
    return if (investment.type == "SIP") 0.0 else investment.amount
}
