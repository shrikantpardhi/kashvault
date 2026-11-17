package com.kashvault.util

import com.kashvault.domain.Insurance
import com.kashvault.domain.Investment
import com.kashvault.domain.Loan
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DueItem(
    val id: String,
    val entityId: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val kind: String,
    val dueOn: LocalDate,
    val daysUntil: Int,
    val canMarkPaid: Boolean,
    val paidThisMonth: Boolean = false,
    val canUndo: Boolean = false,
)

fun monthKey(date: LocalDate): String =
    "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"

fun clampDayOfMonth(year: Int, month: Int, day: Int): Int {
    val last = endOfMonth(LocalDate(year, month, 1)).dayOfMonth
    return day.coerceIn(1, last)
}

fun nextDueDate(
    dueDate: String,
    frequency: String,
    lastProcessedMonth: String?,
    today: LocalDate,
    notBefore: LocalDate? = null,
): LocalDate? {
    val anchor = parseLocalDateOrNull(dueDate) ?: return null
    val normalized = frequency.ifBlank { "Monthly" }
    if (normalized == "OneTime") {
        val date = anchor.takeIf { it >= today } ?: return null
        return date.takeIf { notBefore == null || it >= notBefore }
    }

    val skipThisMonth = lastProcessedMonth == monthKey(today)

    fun candidate(year: Int, month: Int): LocalDate =
        LocalDate(year, month, clampDayOfMonth(year, month, anchor.dayOfMonth))

    val raw = when (normalized) {
        "Yearly" -> {
            var next = candidate(today.year, anchor.monthNumber)
            if (next < today || (skipThisMonth && next.year == today.year && next.monthNumber == today.monthNumber)) {
                next = candidate(today.year + 1, anchor.monthNumber)
            }
            next
        }
        "Quarterly" -> {
            var year = today.year
            var month = today.monthNumber
            var found: LocalDate? = null
            var step = 0
            while (found == null && step < 15) {
                val matches = ((month - anchor.monthNumber) % 12 + 12) % 12 % 3 == 0
                val date = candidate(year, month)
                val thisMonth = date.year == today.year && date.monthNumber == today.monthNumber
                if (matches && date >= today && !(skipThisMonth && thisMonth)) {
                    found = date
                } else {
                    month += 1
                    if (month > 12) {
                        month = 1
                        year += 1
                    }
                    step += 1
                }
            }
            found
        }
        else -> {
            var date = candidate(today.year, today.monthNumber)
            val thisMonth = date.year == today.year && date.monthNumber == today.monthNumber
            if (date < today || (skipThisMonth && thisMonth)) {
                val advanced = date.plus(DatePeriod(months = 1))
                date = candidate(advanced.year, advanced.monthNumber)
            }
            date
        }
    } ?: return null
    return bumpDueToNotBefore(raw, notBefore, normalized, anchor.dayOfMonth)
}

private fun bumpDueToNotBefore(
    date: LocalDate,
    notBefore: LocalDate?,
    frequency: String,
    day: Int,
): LocalDate {
    if (notBefore == null || date >= notBefore) return date
    fun candidate(year: Int, month: Int): LocalDate =
        LocalDate(year, month, clampDayOfMonth(year, month, day))
    var current = date
    repeat(24) {
        if (current >= notBefore) return current
        current = when (frequency) {
            "Yearly" -> candidate(current.year + 1, current.monthNumber)
            "Quarterly" -> {
                val advanced = current.plus(DatePeriod(months = 3))
                candidate(advanced.year, advanced.monthNumber)
            }
            else -> {
                val advanced = current.plus(DatePeriod(months = 1))
                candidate(advanced.year, advanced.monthNumber)
            }
        }
    }
    return current
}

fun openDueOn(
    dueDate: String,
    frequency: String,
    lastProcessedMonth: String?,
    today: LocalDate,
    includeFuture: Boolean,
    notBefore: LocalDate? = null,
): LocalDate? {
    val anchor = parseLocalDateOrNull(dueDate) ?: return null
    val normalized = frequency.ifBlank { "Monthly" }
    if (normalized == "OneTime") {
        if (lastProcessedMonth != null) return null
        if (notBefore != null && anchor < notBefore) return null
        return if (includeFuture || anchor <= today) anchor else null
    }

    fun candidate(year: Int, month: Int): LocalDate =
        LocalDate(year, month, clampDayOfMonth(year, month, anchor.dayOfMonth))

    fun consider(date: LocalDate): Boolean {
        if (notBefore != null && date < notBefore) return false
        if (monthKey(date) == lastProcessedMonth) return false
        return includeFuture || date <= today
    }

    return when (normalized) {
        "Yearly" -> {
            val thisYear = candidate(today.year, anchor.monthNumber)
            when {
                consider(thisYear) -> thisYear
                thisYear > today -> candidate(today.year - 1, anchor.monthNumber).takeIf { consider(it) }
                else -> null
            }
        }
        "Quarterly" -> {
            var year = today.year
            var month = today.monthNumber
            repeat(6) {
                val matches = ((month - anchor.monthNumber) % 12 + 12) % 12 % 3 == 0
                val date = candidate(year, month)
                if (matches && consider(date)) return date
                month -= 1
                if (month < 1) {
                    month = 12
                    year -= 1
                }
            }
            null
        }
        else -> {
            val thisMonth = candidate(today.year, today.monthNumber)
            if (consider(thisMonth)) return thisMonth
            if (includeFuture && notBefore != null && thisMonth < notBefore) {
                val first = candidate(notBefore.year, notBefore.monthNumber)
                return first.takeIf { consider(it) }
            }
            null
        }
    }
}

fun formatDueLabel(daysUntil: Int): String = when {
    daysUntil < 0 -> "Overdue by ${-daysUntil}d"
    daysUntil == 0 -> "Due today"
    daysUntil == 1 -> "Due tomorrow"
    else -> "In $daysUntil days"
}

private fun dueRow(
    entityId: String,
    kind: String,
    title: String,
    subtitle: String,
    amount: Double,
    dueDate: String,
    frequency: String,
    lastProcessedMonth: String?,
    today: LocalDate,
    horizonDays: Int,
    notBefore: LocalDate? = null,
    keepNextMonth: Boolean = false,
): DueItem? {
    val paidThisMonth = lastProcessedMonth == monthKey(today)
    if (paidThisMonth) {
        val anchor = parseLocalDateOrNull(dueDate) ?: return null
        val dueOn = LocalDate(today.year, today.monthNumber, clampDayOfMonth(today.year, today.monthNumber, anchor.dayOfMonth))
        val days = dueOn.toEpochDays() - today.toEpochDays()
        return DueItem(
            id = "$kind-$entityId",
            entityId = entityId,
            title = title,
            subtitle = subtitle,
            amount = amount,
            kind = kind,
            dueOn = dueOn,
            daysUntil = days,
            canMarkPaid = false,
            paidThisMonth = true,
            canUndo = true,
        )
    }
    val open = openDueOn(dueDate, frequency, lastProcessedMonth, today, includeFuture = true, notBefore = notBefore)
    val upcoming = nextDueDate(dueDate, frequency, lastProcessedMonth, today, notBefore = notBefore)
    val dueOn = open ?: upcoming ?: return null
    val days = dueOn.toEpochDays() - today.toEpochDays()
    val nextMonthStart = keepNextMonth &&
        days > 0 &&
        monthKey(dueOn) == monthKey(today.plus(DatePeriod(months = 1)))
    if (days !in -14..horizonDays && !nextMonthStart) return null
    return DueItem(
        id = "$kind-$entityId",
        entityId = entityId,
        title = title,
        subtitle = subtitle,
        amount = amount,
        kind = kind,
        dueOn = dueOn,
        daysUntil = days,
        canMarkPaid = open != null,
    )
}

fun buildDueTimeline(
    investments: List<Investment>,
    insurances: List<Insurance>,
    loans: List<Loan>,
    today: LocalDate = todayLocalDate(),
    horizonDays: Int = 45,
    bills: List<com.kashvault.domain.RecurringBill> = emptyList(),
    sources: List<com.kashvault.domain.PaymentSource> = emptyList(),
    expenses: List<com.kashvault.domain.Expense> = emptyList(),
): List<DueItem> {
    val items = mutableListOf<DueItem>()
    investments.forEach { inv ->
        val frequency = normalizeInvestmentFrequency(inv)
        if (inv.type != "SIP" && frequency == "OneTime") return@forEach
        dueRow(
            entityId = inv.id,
            kind = "sip",
            title = inv.name,
            subtitle = if (frequency == "Monthly") "SIP" else "SIP · $frequency",
            amount = inv.amount,
            dueDate = inv.dueDate ?: return@forEach,
            frequency = frequency,
            lastProcessedMonth = inv.lastProcessedMonth,
            today = today,
            horizonDays = horizonDays,
        )?.let { items += it }
    }
    insurances.forEach { ins ->
        dueRow(
            entityId = ins.id,
            kind = "insurance",
            title = ins.name,
            subtitle = "Insurance · ${frequencyLabel(ins.frequency)}",
            amount = ins.premium,
            dueDate = ins.dueDate,
            frequency = ins.frequency,
            lastProcessedMonth = ins.lastProcessedMonth,
            today = today,
            horizonDays = horizonDays,
        )?.let { items += it }
    }
    loans.forEach { loan ->
        if (loanIsClosed(loan)) return@forEach
        val firstEmi = loanFirstEmiDate(loan.dueDate, loan.startDate)
        dueRow(
            entityId = loan.id,
            kind = "emi",
            title = loan.name,
            subtitle = buildString {
                val left = remainingEmis(loan)
                val tenure = loan.tenureMonths
                append("EMI")
                if (tenure != null && tenure > 0) append(" · $left of $tenure mo left")
                firstEmi?.let { append(" · starts ${formatMonthYear(it)}") }
            },
            amount = loanDueAmount(loan),
            dueDate = loan.dueDate,
            frequency = "Monthly",
            lastProcessedMonth = loan.lastProcessedMonth,
            today = today,
            horizonDays = horizonDays,
            notBefore = firstEmi,
            keepNextMonth = true,
        )?.let { items += it }
    }
    bills.forEach { bill ->
        dueRow(
            entityId = bill.id,
            kind = if (bill.type == "income") DUE_KIND_SALARY else DUE_KIND_BILL,
            title = bill.name,
            subtitle = if (bill.type == "income") "Income · ${frequencyLabel(bill.frequency)}" else "Bill · ${frequencyLabel(bill.frequency)}",
            amount = bill.amount,
            dueDate = bill.dueDate,
            frequency = bill.frequency,
            lastProcessedMonth = bill.lastProcessedMonth,
            today = today,
            horizonDays = horizonDays,
        )?.let { items += it }
    }
    return items.sortedWith(compareBy<DueItem> { if (it.paidThisMonth) 1 else 0 }.thenBy { it.daysUntil }.thenBy { it.title })
}

fun emergencyFundProgress(targetMonths: Int, monthlyRequirement: Double, currentSaved: Double): Double {
    val target = monthlyRequirement * targetMonths
    if (target <= 0.0) return if (currentSaved > 0) 100.0 else 0.0
    return (currentSaved / target * 100.0).coerceIn(0.0, 100.0)
}

fun goalsProgress(current: Double, target: Double): Double {
    if (target <= 0.0) return if (current > 0) 100.0 else 0.0
    return (current / target * 100.0).coerceIn(0.0, 999.0)
}

const val DUE_KIND_SIP = "sip"
const val DUE_KIND_INSURANCE = "insurance"
const val DUE_KIND_EMI = "emi"
const val DUE_KIND_LOAN_PREPAY = "loan_prepay"
const val DUE_KIND_BILL = "bill"
const val DUE_KIND_SALARY = "salary"
const val DUE_KIND_CARD = "card"

data class DuePostCandidate(
    val kind: String,
    val entityId: String,
    val title: String,
    val amount: Double,
    val dueOn: LocalDate,
    val itemSourceId: String?,
    val categoryId: String? = null,
    val expenseType: String = "expense",
    val destinationId: String? = null,
)

fun dueCategoryId(kind: String, fallback: String? = null): String = fallback ?: when (kind) {
    DUE_KIND_SIP -> "cat_wealth"
    DUE_KIND_INSURANCE -> "cat_insurance"
    DUE_KIND_EMI, DUE_KIND_LOAN_PREPAY -> "cat_loans"
    DUE_KIND_SALARY -> "cat_salary"
    DUE_KIND_CARD -> "cat_wealth"
    DUE_KIND_BILL -> "cat_other_exp"
    else -> "cat_other_exp"
}

fun resolveDuePaymentSourceId(itemSourceId: String?, lastPaymentSourceId: String?): String? =
    itemSourceId?.takeIf { it.isNotBlank() } ?: lastPaymentSourceId?.takeIf { it.isNotBlank() }

fun amountsClose(a: Double, b: Double, fraction: Double = 0.02): Boolean {
    if (a <= 0.0 || b <= 0.0) return false
    return kotlin.math.abs(a - b) <= maxOf(1.0, maxOf(a, b) * fraction)
}

fun matchInboxToDue(
    amount: Double,
    type: String,
    suggestedSourceId: String?,
    candidates: List<DuePostCandidate>,
): DuePostCandidate? {
    val typed = candidates.filter { candidate ->
        val typeOk = when (candidate.expenseType) {
            "income" -> type == "income"
            com.kashvault.domain.Constants.TYPE_TRANSFER -> type != "income"
            else -> type != "income"
        }
        typeOk && amountsClose(amount, candidate.amount)
    }
    if (typed.isEmpty()) return null
    val sameSource = typed.filter { it.itemSourceId != null && it.itemSourceId == suggestedSourceId }
    return (sameSource.ifEmpty { typed }).minByOrNull { kotlin.math.abs(it.amount - amount) }
}

fun collectDuePostCandidates(
    investments: List<Investment>,
    insurances: List<Insurance>,
    loans: List<Loan>,
    today: LocalDate = todayLocalDate(),
    includeFuture: Boolean,
    bills: List<com.kashvault.domain.RecurringBill> = emptyList(),
    sources: List<com.kashvault.domain.PaymentSource> = emptyList(),
    expenses: List<com.kashvault.domain.Expense> = emptyList(),
): List<DuePostCandidate> {
    val items = mutableListOf<DuePostCandidate>()
    investments.forEach { inv ->
        val frequency = normalizeInvestmentFrequency(inv)
        if (inv.type != "SIP" && frequency == "OneTime") return@forEach
        val dueOn = openDueOn(
            dueDate = inv.dueDate ?: return@forEach,
            frequency = frequency,
            lastProcessedMonth = inv.lastProcessedMonth,
            today = today,
            includeFuture = includeFuture,
        ) ?: return@forEach
        items += DuePostCandidate(
            kind = DUE_KIND_SIP,
            entityId = inv.id,
            title = inv.name,
            amount = inv.amount,
            dueOn = dueOn,
            itemSourceId = inv.sourceId,
            categoryId = "cat_wealth",
        )
    }
    insurances.forEach { ins ->
        val dueOn = openDueOn(
            dueDate = ins.dueDate,
            frequency = ins.frequency,
            lastProcessedMonth = ins.lastProcessedMonth,
            today = today,
            includeFuture = includeFuture,
        ) ?: return@forEach
        items += DuePostCandidate(
            kind = DUE_KIND_INSURANCE,
            entityId = ins.id,
            title = ins.name,
            amount = ins.premium,
            dueOn = dueOn,
            itemSourceId = ins.sourceId,
            categoryId = "cat_insurance",
        )
    }
    loans.forEach { loan ->
        if (loanIsClosed(loan)) return@forEach
        val dueOn = openDueOn(
            dueDate = loan.dueDate,
            frequency = "Monthly",
            lastProcessedMonth = loan.lastProcessedMonth,
            today = today,
            includeFuture = includeFuture,
            notBefore = loanFirstEmiDate(loan.dueDate, loan.startDate),
        ) ?: return@forEach
        items += DuePostCandidate(
            kind = DUE_KIND_EMI,
            entityId = loan.id,
            title = loan.name,
            amount = loanDueAmount(loan),
            dueOn = dueOn,
            itemSourceId = loan.sourceId,
            categoryId = "cat_loans",
        )
    }
    bills.forEach { bill ->
        val dueOn = openDueOn(
            dueDate = bill.dueDate,
            frequency = bill.frequency.ifBlank { "Monthly" },
            lastProcessedMonth = bill.lastProcessedMonth,
            today = today,
            includeFuture = includeFuture,
        ) ?: return@forEach
        items += DuePostCandidate(
            kind = if (bill.type == "income") DUE_KIND_SALARY else DUE_KIND_BILL,
            entityId = bill.id,
            title = bill.name,
            amount = bill.amount,
            dueOn = dueOn,
            itemSourceId = bill.sourceId,
            categoryId = bill.categoryId,
            expenseType = if (bill.type == "income") "income" else "expense",
        )
    }
    return items
}

fun personInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return if (parts.size == 1) {
        parts[0].take(2).uppercase()
    } else {
        "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}
