package com.kashvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.Constants
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.displayLabel
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.BankVoucherSheet
import com.kashvault.ui.components.TransactionFingerprintDialog
import com.kashvault.ui.components.MerchantMark
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.CardRadius
import com.kashvault.ui.theme.NeoCyan
import com.kashvault.ui.theme.NeoEmerald
import com.kashvault.ui.theme.hexToColor
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.AccountLedgerEntry
import com.kashvault.util.TransactionFingerprints
import com.kashvault.util.calculateAccountLedger
import com.kashvault.util.formatINR
import com.kashvault.util.formatINRSigned
import com.kashvault.util.formatTimestamp
import com.kashvault.util.openingBalanceForDisplayed
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.sourceDisplayedBalance

data class AccountDetailScreen(val sourceId: String) : Screen {
    override val key = "account-detail-$sourceId"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val palette = vaultPalette()

        var filterType by remember { mutableStateOf("all") } // all, debits, credits, transfers
        var searchQuery by remember { mutableStateOf("") }
        var showReconcileDialog by remember { mutableStateOf(false) }
        var voucherExpense by remember { mutableStateOf<com.kashvault.domain.Expense?>(null) }
        var proofExpense by remember { mutableStateOf<com.kashvault.domain.Expense?>(null) }
        var reconcileBalanceText by remember { mutableStateOf("") }

        if (isLoading) {
            LoadingScreen()
            return
        }

        val source = state.paymentSources.find { it.id == sourceId }
        if (source == null) {
            KashVaultScaffold(currentScreen = this, title = "Account statement") { modifier ->
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "Account not found",
                        body = "This account might have been removed.",
                        actionLabel = "Back to accounts",
                        onAction = { if (navigator.canPop) navigator.pop() },
                    )
                }
            }
            return
        }

        val isCreditCard = source.kind == Constants.SOURCE_CREDIT_CARD
        val currentBalance = sourceDisplayedBalance(source, state.expenses)
        val ledger = remember(source, state.expenses) {
            calculateAccountLedger(source, state.expenses)
        }

        val totalCredits = ledger.filter { it.isCredit }.sumOf { it.displayAmount }
        val totalDebits = ledger.filter { !it.isCredit }.sumOf { it.displayAmount }

        val filteredLedger = ledger.filter { entry ->
            when (filterType) {
                "debits" -> !entry.isCredit && !entry.isTransfer
                "credits" -> entry.isCredit && !entry.isTransfer
                "transfers" -> entry.isTransfer
                else -> true
            }
        }.filter { entry ->
            val needle = searchQuery.trim().lowercase()
            if (needle.isEmpty()) true
            else {
                val cat = state.categories.find { it.id == entry.expense.categoryId }
                listOfNotNull(entry.expense.note, cat?.name).any { it.lowercase().contains(needle) }
            }
        }

        KashVaultScaffold(
            currentScreen = this,
            title = source.displayLabel(),
        ) { modifier ->
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header Card
                    item {
                        KashCard(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentPadding = 16.dp,
                            cardElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(hexToColor(source.color).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(source.icon.ifBlank { if (isCreditCard) "💳" else "🏦" }, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Column {
                                        Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (isCreditCard) "Credit Card · ending ${source.last4 ?: "XXXX"}" else "Bank Account · ending ${source.last4 ?: "XXXX"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    reconcileBalanceText = if (currentBalance % 1.0 == 0.0) currentBalance.toLong().toString() else currentBalance.toString()
                                    showReconcileDialog = true
                                }) {
                                    Text("Reconcile")
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                if (isCreditCard) "Outstanding Due" else "Available Balance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                formatINR(currentBalance, alwaysShowDecimals = currentBalance % 1.0 != 0.0),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCreditCard && currentBalance > 0) palette.spend else palette.income,
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.incomeContainer.copy(alpha = 0.6f),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Total Money In", style = MaterialTheme.typography.labelSmall, color = palette.income)
                                        Text(formatINR(totalCredits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = palette.income)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = palette.spendContainer.copy(alpha = 0.6f),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Total Money Out", style = MaterialTheme.typography.labelSmall, color = palette.spend)
                                        Text(formatINR(totalDebits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = palette.spend)
                                    }
                                }
                            }
                        }
                    }

                    // Filter & Search Controls
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VaultOutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search account transactions") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = filterType == "all",
                                    onClick = { filterType = "all" },
                                    label = { Text("All (${ledger.size})") },
                                )
                                FilterChip(
                                    selected = filterType == "debits",
                                    onClick = { filterType = "debits" },
                                    label = { Text("Debits") },
                                )
                                FilterChip(
                                    selected = filterType == "credits",
                                    onClick = { filterType = "credits" },
                                    label = { Text("Credits") },
                                )
                                FilterChip(
                                    selected = filterType == "transfers",
                                    onClick = { filterType = "transfers" },
                                    label = { Text("Transfers") },
                                )
                            }
                        }
                    }

                    // Passbook / Statement Table
                    if (filteredLedger.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (searchQuery.isNotEmpty() || filterType != "all") "No matching transactions" else "No transactions on this account",
                                body = if (searchQuery.isNotEmpty() || filterType != "all") "Try changing the filter or search term." else "Transactions linked to this bank account will appear here with running balance.",
                                icon = "📖",
                                actionLabel = "Add transaction",
                                onAction = { navigator.push(AddExpenseScreen()) },
                            )
                        }
                    } else {
                        items(filteredLedger, key = { it.expense.id }) { entry ->
                            val expense = entry.expense
                            val category = state.categories.find { it.id == expense.categoryId }
                            val isOutflow = !entry.isCredit
                            val title = when {
                                entry.isTransfer -> {
                                    val other = if (expense.sourceId == source.id) {
                                        state.paymentSources.find { it.id == expense.destinationId }?.displayLabel() ?: "Account"
                                    } else {
                                        state.paymentSources.find { it.id == expense.sourceId }?.displayLabel() ?: "Account"
                                    }
                                    if (expense.sourceId == source.id) "Transfer to $other" else "Transfer from $other"
                                }
                                expense.note?.isNotBlank() == true -> expense.note.trim()
                                else -> category?.name ?: "Transaction"
                            }
                            val subtitle = listOfNotNull(
                                category?.name.takeIf { !entry.isTransfer && expense.note?.isNotBlank() == true },
                                formatTimestamp(expense.timestamp),
                            ).joinToString(" · ")

                            KashCard(
                                onClick = { navigator.push(AddExpenseScreen(expenseId = expense.id)) },
                                contentPadding = 12.dp,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MerchantMark(
                                        name = title,
                                        glyph = if (entry.isTransfer) "⇄" else category?.icon,
                                        colorHex = category?.color,
                                        size = 38,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatINRSigned(
                                                amount = entry.displayAmount,
                                                type = if (entry.isTransfer) Constants.TYPE_TRANSFER else if (entry.isCredit) "income" else "expense",
                                                alwaysShowDecimals = entry.displayAmount % 1.0 != 0.0,
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                entry.isTransfer -> MaterialTheme.colorScheme.primary
                                                entry.isCredit -> palette.income
                                                else -> palette.spend
                                            },
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                         Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        ) {
                                            Text(
                                                "Bal: ${formatINR(entry.runningBalance, alwaysShowDecimals = entry.runningBalance % 1.0 != 0.0)}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Action Button to quickly add transaction on this account
                FloatingActionButton(
                    onClick = { navigator.push(AddExpenseScreen()) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction")
                }
            }
        }

        if (showReconcileDialog) {
            AlertDialog(
                onDismissRequest = { showReconcileDialog = false },
                title = { Text("Reconcile Bank Balance") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter the exact closing balance shown on your bank statement or SMS. The app will adjust the opening balance to ensure your account balance matches perfectly.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        VaultOutlinedTextField(
                            value = reconcileBalanceText,
                            onValueChange = { reconcileBalanceText = it },
                            label = { Text("Actual Bank Balance (${Constants.RUPEE_SIGN})") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val entered = parseMoneyInput(reconcileBalanceText)
                        if (entered.isFinite()) {
                            val newOpening = openingBalanceForDisplayed(source, state.expenses, entered)
                            vm.savePaymentSource(source.copy(openingBalance = newOpening))
                            showReconcileDialog = false
                        }
                    }) {
                        Text("Reconcile")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReconcileDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
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
