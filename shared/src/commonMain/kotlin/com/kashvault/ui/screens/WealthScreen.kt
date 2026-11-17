package com.kashvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.FinanceSnapshot
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.banks
import com.kashvault.domain.creditCards
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.MetricTile
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.components.ProgressStat
import com.kashvault.ui.components.SectionHeader
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.navigation.openMainTab
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.DUE_KIND_BILL
import com.kashvault.util.DUE_KIND_CARD
import com.kashvault.util.DUE_KIND_EMI
import com.kashvault.util.DUE_KIND_INSURANCE
import com.kashvault.util.DUE_KIND_SALARY
import com.kashvault.util.DUE_KIND_SIP
import com.kashvault.util.DueItem
import com.kashvault.util.buildDueTimeline
import com.kashvault.util.emergencyFundProgress
import com.kashvault.util.formatDueLabel
import com.kashvault.util.formatINR
import com.kashvault.util.getLendingTotals
import com.kashvault.util.getLoanOutstandingTotal
import com.kashvault.util.goalsProgress
import com.kashvault.util.resolveDuePaymentSourceId
import com.kashvault.util.sourceDisplayedBalance

object WealthScreen : Screen {
    override val key = "wealth"

    @Composable
    override fun Content() {
        KashVaultScaffold(currentScreen = this, title = "Wealth") { WealthPane(it) }
    }
}

@Composable
internal fun WealthPane(modifier: Modifier = Modifier) {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val breakdown = remember(state) {
            vm.getNetWorthBreakdown()
        }
        val lien = remember(state.investments, state.insurances, state.loans, state.recurringBills) {
            vm.getLienAmount()
        }
        val lending = remember(state.borrowLend, state.people) {
            getLendingTotals(state.borrowLend, state.people)
        }
        var paying by remember { mutableStateOf<com.kashvault.util.DueItem?>(null) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        LaunchedEffect(Unit) { vm.processDueItems() }

        val dues = buildDueTimeline(
            state.investments,
            state.insurances,
            state.loans,
            bills = state.recurringBills,
            sources = state.paymentSources,
            expenses = state.expenses,
        )
        val overdue = dues.count { it.daysUntil < 0 }
        val autoBlocked = dues.any { due ->
            due.canMarkPaid && resolveDuePaymentSourceId(
                itemSourceId = duePaidFromId(due, state),
                lastPaymentSourceId = state.lastPaymentSourceId,
            ) == null
        }
        val goalTarget = state.savingsGoals.sumOf { it.target }
        val goalCurrent = state.savingsGoals.sumOf { it.current }
        val colors = vaultPalette()
        val investedPercent = if (breakdown.netWorth > 0) {
            (breakdown.investments / (breakdown.investments + breakdown.liquidity).coerceAtLeast(1.0) * 100.0)
        } else if (breakdown.investments > 0) 100.0 else 0.0
        val emergencyPct = emergencyFundProgress(
            state.emergencyFund.targetMonths,
            state.emergencyFund.monthlyRequirement,
            state.emergencyFund.currentSaved,
        )
        val banks = state.paymentSources.banks()
        val cards = state.paymentSources.creditCards()

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricTile(
                            label = "Net worth",
                            value = formatINR(breakdown.netWorth),
                            caption = "After loans & dues",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            accent = colors.wealth,
                            containerColor = colors.wealthContainer,
                        )
                        if (lien > 0) {
                            MetricTile(
                                label = "Lien",
                                value = formatINR(lien),
                                caption = "Committed dues",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                accent = colors.due,
                                containerColor = colors.dueContainer,
                            )
                        }
                        MetricTile(
                            label = "Lending",
                            value = formatINR(lending.overall),
                            caption = if (lending.byPerson.isEmpty()) "No open logs" else "${lending.byPerson.size} people",
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { navigator.openMainTab(LendingScreen) },
                            accent = colors.lending,
                            containerColor = colors.lendingContainer,
                        )
                    }
                }
                if (state.paymentSources.isNotEmpty()) {
                    item {
                        SectionHeader(
                            "Banks & cards",
                            action = "Manage",
                            onAction = { navigator.push(PaymentSourcesScreen) },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cards.forEach { card ->
                                val owed = sourceDisplayedBalance(card, state.expenses)
                                KashCard(
                                    contentPadding = 12.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(card.icon.ifBlank { "💳" })
                                            Column {
                                                Text(
                                                    card.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    if (owed > 0) "Due this cycle" else "No dues",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            formatINR(owed.coerceAtLeast(0.0)),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (owed > 0) colors.spend else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            banks.forEach { bank ->
                                val bal = sourceDisplayedBalance(bank, state.expenses)
                                KashCard(
                                    contentPadding = 12.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(bank.icon.ifBlank { "🏦" })
                                            Column {
                                                Text(
                                                    bank.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    "Available balance",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            formatINR(bal),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.income,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("Due timeline") }
                if (overdue > 0) {
                    item {
                        KashCard(containerColor = colors.dueContainer) {
                            Text(
                                "$overdue overdue. They post automatically when a paid-from account is set, or Mark paid now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.due,
                            )
                        }
                    }
                }
                if (autoBlocked) {
                    item {
                        KashCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                if (state.paymentSources.isEmpty()) {
                                    "Add a bank or card in Profile so dues can post automatically on the due date. You can still Mark paid after picking an account."
                                } else {
                                    "Some dues have no paid-from account, so they will not post automatically. Mark paid to choose one, or set it on the SIP / loan."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                if (dues.isEmpty()) {
                    item { EmptyState("Nothing due soon", "SIPs, bills, insurance, salary, and EMIs in the next 45 days will land here.") }
                } else {
                    items(dues.distinctBy { it.id }, key = { it.id }) { due ->
                        KashCard(
                            onClick = {
                                navigator.push(
                                    when (due.kind) {
                                        DUE_KIND_EMI -> LoansScreen
                                        DUE_KIND_BILL, DUE_KIND_SALARY -> BillsScreen
                                        DUE_KIND_CARD -> PaymentSourcesScreen
                                        else -> RecurringScreen
                                    },
                                )
                            },
                            containerColor = when {
                                due.paidThisMonth -> colors.incomeContainer
                                due.daysUntil < 0 -> colors.dueContainer
                                due.daysUntil == 0 -> colors.lendingContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            contentPadding = 10.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        due.title,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        if (due.paidThisMonth) {
                                            "${due.subtitle} · Paid · ${due.dueOn}"
                                        } else {
                                            "${due.subtitle} · ${formatDueLabel(due.daysUntil)} · ${due.dueOn}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (due.daysUntil < 0 && !due.paidThisMonth) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    formatINR(due.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (due.canMarkPaid) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { vm.skipDue(due.kind, due.entityId) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) {
                                        Text("Skip", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    FilledTonalButton(
                                        onClick = { paying = due },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            if (due.kind == DUE_KIND_SALARY) "Mark received" else "Mark paid",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { SectionHeader("Progress") }
                item {
                    ProgressStat(
                        title = "Invested",
                        caption = "${formatINR(breakdown.investments)} across ${state.investments.size} holding(s)",
                        percent = investedPercent,
                        color = colors.wealth,
                        onClick = { navigator.push(RecurringScreen) },
                    )
                }
                item {
                    ProgressStat(
                        title = "Goals",
                        caption = "${formatINR(goalCurrent)} of ${formatINR(goalTarget)}",
                        percent = goalsProgress(goalCurrent, goalTarget),
                        color = colors.goal,
                        onClick = { navigator.push(GoalsScreen) },
                    )
                }
                item {
                    ProgressStat(
                        title = "Emergency",
                        caption = "${formatINR(state.emergencyFund.currentSaved)} · ${state.emergencyFund.targetMonths} month target",
                        percent = emergencyPct,
                        color = colors.leftover,
                        onClick = { navigator.push(GoalsScreen) },
                    )
                }

                item { SectionHeader("Manage") }
                item {
                    val billCount = state.recurringBills.count { it.type != "income" }
                    val incomeCount = state.recurringBills.count { it.type == "income" }
                    val outstandingCount = state.loans.count { getLoanOutstandingTotal(it) > 0.0 }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ManageTile(
                                title = "Bills & Salary",
                                caption = "$billCount Bills · $incomeCount Income",
                                onClick = { navigator.push(BillsScreen) },
                                modifier = Modifier.weight(1f),
                            )
                            ManageTile(
                                title = "Recurring",
                                caption = "${state.investments.size} Investments · ${state.insurances.size} Insurances",
                                onClick = { navigator.push(RecurringScreen) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ManageTile(
                                title = "Loans & EMI",
                                caption = "$outstandingCount Outstanding · ${state.loans.size} EMI",
                                onClick = { navigator.push(LoansScreen) },
                                modifier = Modifier.weight(1f),
                            )
                            ManageTile(
                                title = "Goals",
                                caption = if (state.savingsGoals.isEmpty()) {
                                    "Create a savings target"
                                } else {
                                    "${state.savingsGoals.size} goals · ${formatINR(goalCurrent)} saved"
                                },
                                onClick = { navigator.push(GoalsScreen) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

        paying?.let { due ->
            var sourceId by remember(due.id) {
                mutableStateOf(duePaidFromId(due, state) ?: state.lastPaymentSourceId)
            }
            AlertDialog(
                onDismissRequest = { paying = null },
                title = { Text(if (due.kind == DUE_KIND_SALARY) "Mark ${due.title} received" else "Mark ${due.title} paid") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            "${formatINR(due.amount)} · ${due.subtitle}. Posts on the due date, or now if you pay early. Skip leaves this month unpaid without posting.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.paymentSources.isEmpty()) {
                            Text("Add a bank or card first so this can be paid from an account.")
                        } else {
                            PaidFromChips(
                                sources = state.paymentSources,
                                selectedId = sourceId,
                                onSelect = { sourceId = it },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (sourceId.isNullOrBlank()) return@TextButton
                            vm.markDuePaid(due.kind, due.entityId, sourceId)
                            paying = null
                        },
                        enabled = !sourceId.isNullOrBlank(),
                    ) { Text(if (due.kind == DUE_KIND_SALARY) "Mark received" else "Mark paid") }
                },
                dismissButton = {
                    TextButton(onClick = { paying = null }) { Text("Cancel") }
                },
            )
        }
}

private fun duePaidFromId(due: DueItem, state: FinanceSnapshot): String? = when (due.kind) {
    DUE_KIND_SIP -> state.investments.find { it.id == due.entityId }?.sourceId
    DUE_KIND_INSURANCE -> state.insurances.find { it.id == due.entityId }?.sourceId
    DUE_KIND_EMI -> state.loans.find { it.id == due.entityId }?.sourceId
    DUE_KIND_BILL, DUE_KIND_SALARY -> state.recurringBills.find { it.id == due.entityId }?.sourceId
    DUE_KIND_CARD -> state.paymentSources.find { it.id == due.entityId }?.payFromSourceId
    else -> null
}

@Composable
private fun ManageTile(
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
