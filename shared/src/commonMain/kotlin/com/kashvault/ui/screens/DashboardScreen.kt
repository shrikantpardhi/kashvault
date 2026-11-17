package com.kashvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.displayLabel
import com.kashvault.domain.isSpend
import com.kashvault.domain.isTransfer
import com.kashvault.ui.components.BudgetRing
import com.kashvault.ui.components.CategorySpendingBar
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.MetricTile
import com.kashvault.ui.components.PersonAvatar
import com.kashvault.ui.components.ProgressBar
import com.kashvault.ui.components.SectionHeader
import com.kashvault.ui.components.BankVoucherSheet
import com.kashvault.ui.components.TransactionFingerprintDialog
import com.kashvault.ui.components.NeoGlowingChip
import com.kashvault.ui.components.TransactionRow
import com.kashvault.ui.components.vaultGradient
import com.kashvault.ui.haptics.platformHapticTick
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.CardRadius
import com.kashvault.ui.theme.NeoCyan
import com.kashvault.ui.theme.NeoEmerald
import com.kashvault.ui.theme.NeoFintechMeshGradient
import com.kashvault.ui.theme.ScreenGap
import com.kashvault.ui.theme.hexToColor
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.TransactionFingerprints
import com.kashvault.util.availableCash
import com.kashvault.util.cardOutstandingTotal
import com.kashvault.util.formatDisplayDate
import com.kashvault.util.formatINR
import com.kashvault.util.formatTimestamp
import com.kashvault.util.getFilteredExpenses
import com.kashvault.util.greetingForHour
import com.kashvault.util.monthPulse
import com.kashvault.util.nextCreditCardDue
import com.kashvault.util.sourceDisplayedBalance
import com.kashvault.util.todayLocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object DashboardScreen : Screen {
    override val key = "dashboard"

    @Composable
    override fun Content() {
        KashVaultScaffold(currentScreen = this, title = "KashVault") { DashboardPane(it) }
    }
}

@Composable
internal fun DashboardPane(modifier: Modifier = Modifier) {
    val vm = LocalFinanceViewModel.current
    val state by vm.state.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val photoEpoch by vm.profilePhotoEpoch.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    var isBudgetExpanded by remember { mutableStateOf(false) }
    var voucherExpense by remember { mutableStateOf<Expense?>(null) }
    var proofExpense by remember { mutableStateOf<Expense?>(null) }

    if (isLoading) {
        LoadingScreen()
        return
    }

    LaunchedEffect(Unit) { vm.processDueItems() }

    val hasBanks = state.paymentSources.any { it.kind != Constants.SOURCE_CREDIT_CARD }
    val cash = if (hasBanks) {
        availableCash(state.paymentSources, state.expenses)
    } else {
        vm.getGlobalLiquidity()
    }
    val cardBills = cardOutstandingTotal(state.paymentSources, state.expenses)
    val lien = vm.getLienAmount()
    val pulse = monthPulse(state.expenses) { id -> state.categories.find { it.id == id }?.name ?: "Other" }
    val recent = state.expenses.sortedByDescending { it.timestamp }.take(8)
    val pendingInbox = state.inboxItems.size
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val fullName = state.userProfile?.name?.trim().orEmpty()
    val name = fullName.takeIf { it.isNotEmpty() }?.substringBefore(" ")
    val hello = buildString {
        append(greetingForHour(hour))
        if (name != null) append(", ").append(name)
    }
    val banks = state.paymentSources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }
    val cards = state.paymentSources.filter { it.kind == Constants.SOURCE_CREDIT_CARD }
    val colors = vaultPalette()
    val budget = vm.getBudgetUsage()
    val budgetPercent = if (budget.totalLimit > 0) {
        budget.overallPercent
    } else if (pulse.income > 0) {
        (pulse.spend / pulse.income) * 100
    } else {
        0.0
    }
    val photo = rememberProfilePhoto(state.userProfile?.imagePath, photoEpoch)

    val topCategorySpends = remember(state.expenses, state.categories) {
        val monthlySpends = getFilteredExpenses(state.expenses, "monthly").filter { it.isSpend() }
        val total = monthlySpends.sumOf { it.amount }
        if (total <= 0.0) emptyList()
        else {
            monthlySpends.groupBy { it.categoryId }
                .mapNotNull { (catId, exps) ->
                    val cat = state.categories.find { it.id == catId } ?: return@mapNotNull null
                    val sum = exps.sumOf { it.amount }
                    val pct = (sum / total) * 100.0
                    Triple(cat, sum, pct)
                }
                .sortedByDescending { it.second }
                .take(4)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(ScreenGap),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PersonAvatar(fullName.ifBlank { "You" }, size = 40, image = photo)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hello, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatDisplayDate(todayLocalDate()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricTile(
                        label = "Income",
                        value = formatINR(pulse.income),
                        caption = "This month",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        accent = colors.income,
                        containerColor = colors.incomeContainer,
                    )
                    MetricTile(
                        label = "Expense",
                        value = formatINR(pulse.spend),
                        caption = "This month",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        accent = colors.spend,
                        containerColor = colors.spendContainer,
                    )
                    MetricTile(
                        label = "Balance",
                        value = formatINR(pulse.leftover),
                        caption = "Left this month",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        accent = colors.leftover,
                        containerColor = colors.leftoverContainer,
                    )
                }
            }

            item {
                AvailableCashCard(
                    cash = cash,
                    hasBanks = hasBanks,
                    banks = banks,
                    cards = cards,
                    expenses = state.expenses,
                    cardBills = cardBills,
                    lien = lien,
                    onManage = { navigator.push(PaymentSourcesScreen) },
                    onAccountClick = { navigator.push(AccountDetailScreen(it)) },
                )
            }

            item {
                KashCard(contentPadding = 14.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isBudgetExpanded = !isBudgetExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        "Budget & Spending",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        imageVector = if (isBudgetExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isBudgetExpanded) "Collapse budget" else "Expand budget",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (budget.totalLimit > 0) formatINR(budget.totalSpent) else formatINR(pulse.spend),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (budget.totalLimit > 0) {
                                        "${formatINR(budget.totalLimit)} monthly limit · ${if (isBudgetExpanded) "Tap to collapse" else "Tap for breakdown"}"
                                    } else if (pulse.income > 0) {
                                        "Of ${formatINR(pulse.income)} income · ${if (isBudgetExpanded) "Tap to collapse" else "Tap for breakdown"}"
                                    } else {
                                        if (isBudgetExpanded) "Tap to collapse" else "Tap to view category breakdown"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BudgetRing(
                                percent = budgetPercent,
                                color = if (budget.anyOver) colors.spend else MaterialTheme.colorScheme.primary,
                            )
                        }

                        AnimatedVisibility(
                            visible = isBudgetExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (budget.categories.isNotEmpty()) {
                                    budget.categories.forEach { catUsage ->
                                        val catColor = hexToColor(catUsage.color)
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(catUsage.icon, style = MaterialTheme.typography.bodyMedium)
                                                    Text(
                                                        catUsage.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                }
                                                Text(
                                                    "${formatINR(catUsage.spent)} / ${formatINR(catUsage.limit)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (catUsage.isOverLimit) colors.spend else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = if (catUsage.isOverLimit) FontWeight.SemiBold else FontWeight.Normal,
                                                )
                                            }
                                            ProgressBar(
                                                percent = catUsage.percent,
                                                color = if (catUsage.isOverLimit) colors.spend else catColor,
                                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                            )
                                        }
                                    }
                                } else if (topCategorySpends.isNotEmpty()) {
                                    Text(
                                        "Spending by category this month",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    topCategorySpends.forEach { (cat, amount, pct) ->
                                        CategorySpendingBar(
                                            name = cat.name,
                                            icon = cat.icon,
                                            amount = amount,
                                            percent = pct,
                                            color = hexToColor(cat.color),
                                            onClick = { navigator.push(HistoryScreen) },
                                        )
                                    }
                                } else {
                                    Text(
                                        "No spending recorded this month yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { navigator.push(BudgetScreen) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    ) {
                                        Text("Set category limits ›", style = MaterialTheme.typography.labelMedium)
                                    }
                                    TextButton(
                                        onClick = { isBudgetExpanded = false },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    ) {
                                        Text("Collapse", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                KashCard(onClick = { navigator.push(InboxScreen) }, contentPadding = 14.dp) {
                    Text("Inbox", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (pendingInbox > 0) {
                            "$pendingInbox payment${if (pendingInbox == 1) "" else "s"} waiting"
                        } else {
                            "No pending messages"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Recent transactions",
                    action = "View more",
                    onAction = { navigator.push(HistoryScreen) },
                )
            }

            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing logged yet",
                        body = "Tap the + button below to record your first expense, income, or bank transfer.",
                        icon = "⚡",
                        actionLabel = "Add first spend",
                        onAction = { navigator.push(AddExpenseScreen()) },
                    )
                }
            } else {
                item {
                    KashCard {
                        recent.forEachIndexed { index, expense ->
                            val category = state.categories.find { it.id == expense.categoryId }
                            val source = state.paymentSources.find { it.id == expense.sourceId }
                            val destination = state.paymentSources.find { it.id == expense.destinationId }
                            val isTransfer = expense.isTransfer()
                            val destName = destination?.displayLabel() ?: expense.destinationId?.removePrefix("goal:")?.let { "Goal: $it" } ?: "Transfer"
                            val merchant = when {
                                isTransfer -> "Transfer to $destName"
                                expense.note?.trim()?.isNotEmpty() == true -> expense.note.trim()
                                else -> category?.name ?: "Unknown"
                            }
                            val subtitle = when {
                                isTransfer -> {
                                    val fromName = source?.displayLabel() ?: "Account"
                                    "$fromName ➔ $destName · ${formatTimestamp(expense.timestamp)}"
                                }
                                else -> {
                                    listOfNotNull(
                                        source?.displayLabel(),
                                        formatTimestamp(expense.timestamp),
                                    ).joinToString(" · ")
                                }
                            }
                            TransactionRow(
                                icon = if (isTransfer) "⇄" else (category?.icon ?: "₹"),
                                title = merchant,
                                subtitle = subtitle,
                                amount = expense.amount,
                                type = expense.type ?: "expense",
                                onClick = { navigator.push(AddExpenseScreen(expenseId = expense.id)) },
                                chipColor = if (isTransfer) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else hexToColor(category?.color.orEmpty()).copy(alpha = 0.28f),
                                chipLabel = if (isTransfer) "Transfer" else category?.name,
                            )
                            if (index != recent.lastIndex) Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                runCatching { platformHapticTick() }
                navigator.push(AddExpenseScreen())
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add transaction")
        }

        voucherExpense?.let { exp ->
            BankVoucherSheet(
                voucher = TransactionFingerprints.buildTransactionReceipt(exp, state.paymentSources, state.expenses),
                onDismiss = { voucherExpense = null },
            )
        }

        proofExpense?.let { exp ->
            TransactionFingerprintDialog(
                fingerprint = TransactionFingerprints.fingerprint(exp, state.expenses),
                onDismiss = { proofExpense = null },
                onViewVoucher = {
                    val target = proofExpense
                    proofExpense = null
                    voucherExpense = target
                },
            )
        }
    }
}

@Composable
private fun AvailableCashCard(
    cash: Double,
    hasBanks: Boolean,
    banks: List<PaymentSource>,
    cards: List<PaymentSource>,
    expenses: List<Expense>,
    cardBills: Double,
    lien: Double = 0.0,
    onManage: () -> Unit,
    onAccountClick: (String) -> Unit = {},
) {
    val onGradient = Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(NeoFintechMeshGradient)
            .border(1.dp, NeoCyan.copy(alpha = 0.35f), RoundedCornerShape(CardRadius))
            .clickable(onClick = onManage)
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeoGlowingChip(
                    text = "ON DEVICE",
                    accentColor = NeoCyan,
                )
                Text(
                    "Manage ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = onGradient.copy(alpha = 0.85f),
                )
            }
            Text(
                formatINR(cash, alwaysShowDecimals = cash % 1.0 != 0.0),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = onGradient,
            )
            if (banks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                banks.take(5).forEach { bank ->
                    val bBal = sourceDisplayedBalance(bank, expenses)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAccountClick(bank.id) }
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🏦 ${bank.name}", style = MaterialTheme.typography.bodySmall, color = onGradient.copy(alpha = 0.9f))
                        Text(
                            formatINR(bBal, alwaysShowDecimals = bBal % 1.0 != 0.0),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onGradient,
                        )
                    }
                }
            } else {
                Text(
                    "Income minus spend. Add a bank to track balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = onGradient.copy(alpha = 0.78f),
                )
            }
            if (cards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                cards.take(3).forEach { card ->
                    val owed = sourceDisplayedBalance(card, expenses)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onAccountClick(card.id) }
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "💳 ${card.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = onGradient.copy(alpha = 0.9f),
                        )
                        Text(
                            if (owed > 0) "${formatINR(owed, alwaysShowDecimals = owed % 1.0 != 0.0)} due" else "No dues",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onGradient,
                        )
                    }
                }
            } else if (cardBills > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💳 Credit cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = onGradient.copy(alpha = 0.86f),
                    )
                    Text(
                        "${formatINR(cardBills)} due",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = onGradient,
                    )
                }
            }
            if (lien > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🔒 Committed / Lien",
                        style = MaterialTheme.typography.bodySmall,
                        color = onGradient.copy(alpha = 0.86f),
                    )
                    Text(
                        formatINR(lien),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = onGradient.copy(alpha = 0.95f),
                    )
                }
            }
            if (!hasBanks && cards.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Add bank or card",
                    style = MaterialTheme.typography.labelLarge,
                    color = onGradient,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
