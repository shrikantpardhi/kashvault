package com.kashvault.util

import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.SyncSettings
import kotlinx.datetime.LocalDate

data class NextCardDue(
    val name: String,
    val daysUntil: Int,
    val outstanding: Double,
)

data class SpendDecision(
    val runwayDays: Int,
    val runwayLabel: String,
    val inboxCount: Int,
    val inboxLabel: String,
    val cardLabel: String?,
    val backupLabel: String,
    val backupNeedsAction: Boolean,
)

fun nextCreditCardDue(
    sources: List<PaymentSource>,
    expenses: List<Expense>,
    today: LocalDate = todayLocalDate(),
): NextCardDue? {
    val cards = sources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }
    val ranked = cards.mapNotNull { card ->
        val dueOn = nextDueDate(
            dueDate = card.statementDueDate ?: return@mapNotNull null,
            frequency = "Monthly",
            lastProcessedMonth = card.lastProcessedMonth,
            today = today,
        ) ?: return@mapNotNull null
        NextCardDue(
            name = card.name,
            daysUntil = dueOn.toEpochDays() - today.toEpochDays(),
            outstanding = sourceDisplayedBalance(card, expenses),
        )
    }.sortedWith(compareBy<NextCardDue> { it.daysUntil }.thenByDescending { it.outstanding })
    ranked.firstOrNull { it.outstanding > 0 }?.let { return it }
    return ranked.firstOrNull()
}

fun spendDecision(
    input: LiquidityInput,
    inboxCount: Int,
    cardBills: Double,
    nextCard: NextCardDue?,
    sync: SyncSettings,
    today: LocalDate = todayLocalDate(),
): SpendDecision {
    val forecast = heuristicLiquidityForecast(input)
    val runwayLabel = when {
        forecast.runwayDays >= 999 -> "Income covers spend"
        forecast.runwayDays <= 0 -> "No cash runway"
        forecast.runwayDays == 1 -> "1 day of cash"
        else -> "${forecast.runwayDays} days of cash"
    }
    val inboxLabel = when (inboxCount) {
        0 -> "Inbox is clear"
        1 -> "1 payment waiting"
        else -> "$inboxCount payments waiting"
    }
    val cardLabel = when {
        nextCard != null && nextCard.outstanding > 0 -> {
            val due = if (nextCard.daysUntil <= 0) "due today" else "due in ${nextCard.daysUntil}d"
            "${nextCard.name} ${formatINR(nextCard.outstanding)} $due"
        }
        cardBills > 0 -> "Cards outstanding ${formatINR(cardBills)}"
        else -> null
    }
    val last = sync.lastBackupAt ?: sync.cloudBackupAt
    val stale = backupIsStale(last, today)
    val backupLabel = when {
        last.isNullOrBlank() -> "No backup yet"
        stale -> "Backup older than a week"
        else -> "Last backup ${formatQuietBackupDay(last)}"
    }
    return SpendDecision(
        runwayDays = forecast.runwayDays,
        runwayLabel = runwayLabel,
        inboxCount = inboxCount,
        inboxLabel = inboxLabel,
        cardLabel = cardLabel,
        backupLabel = backupLabel,
        backupNeedsAction = last.isNullOrBlank() || stale,
    )
}

fun formatQuietBackupDay(value: String): String = value.trim().take(10)
