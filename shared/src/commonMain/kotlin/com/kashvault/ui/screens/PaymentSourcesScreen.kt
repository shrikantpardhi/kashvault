package com.kashvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.CATEGORY_COLOR_OPTIONS
import com.kashvault.domain.CATEGORY_EMOJI_OPTIONS
import com.kashvault.domain.Constants
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.banks
import com.kashvault.domain.creditCards
import com.kashvault.domain.defaultSourceColor
import com.kashvault.domain.defaultSourceIcon
import com.kashvault.domain.displayLabel
import com.kashvault.domain.kindLabel
import com.kashvault.domain.keywordsFromText
import com.kashvault.domain.keywordsToText
import com.kashvault.inbox.defaultSourceTags
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.SecondaryButton
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.availableCash
import com.kashvault.util.cardOutstandingTotal
import com.kashvault.util.formatINR
import com.kashvault.util.formatTimestamp
import com.kashvault.domain.isSpend
import com.kashvault.util.openingBalanceForDisplayed
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.sourceDisplayedBalance

object PaymentSourcesScreen : Screen {
    override val key = "payment-sources"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val palette = vaultPalette()

        var showEditor by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<PaymentSource?>(null) }
        var pendingDelete by remember { mutableStateOf<PaymentSource?>(null) }

        var name by remember { mutableStateOf("") }
        var last4 by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(Constants.SOURCE_BANK) }
        var icon by remember { mutableStateOf(defaultSourceIcon(Constants.SOURCE_BANK)) }
        var color by remember { mutableStateOf(defaultSourceColor(Constants.SOURCE_BANK)) }
        var keywordsText by remember { mutableStateOf("") }
        var balanceText by remember { mutableStateOf("") }
        var payFromId by remember { mutableStateOf<String?>(null) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        fun resetDraft(forKind: String = Constants.SOURCE_BANK) {
            name = ""
            last4 = ""
            kind = forKind
            icon = defaultSourceIcon(forKind)
            color = defaultSourceColor(forKind)
            keywordsText = ""
            balanceText = ""
            payFromId = null
        }

        fun startEdit(source: PaymentSource) {
            editing = source
            showEditor = true
            name = source.name
            last4 = source.last4.orEmpty()
            kind = source.kind
            icon = source.icon
            color = source.color
            keywordsText = keywordsToText(source.keywords)
            val displayed = sourceDisplayedBalance(source, state.expenses)
            balanceText = if (displayed % 1.0 == 0.0) displayed.toLong().toString() else displayed.toString()
            payFromId = source.payFromSourceId
        }

        val banks = state.paymentSources.banks()
        val cards = state.paymentSources.creditCards()
        val totalBankBalance = availableCash(state.paymentSources, state.expenses)
        val totalCardOutstanding = cardOutstandingTotal(state.paymentSources, state.expenses)
        val sources = state.paymentSources.sortedWith(
            compareBy<PaymentSource> { it.kind != Constants.SOURCE_BANK }.thenBy { it.name.lowercase() }
        )

        KashVaultScaffold(currentScreen = this, title = "Banks & cards") { modifier ->
            Column(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.paymentSources.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                KashCard(
                                    modifier = Modifier.weight(1f),
                                    containerColor = palette.incomeContainer,
                                    contentPadding = 12.dp,
                                ) {
                                    Text(
                                        "Bank balance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.income,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatINR(totalBankBalance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.income,
                                    )
                                    Text(
                                        "${banks.size} account${if (banks.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                KashCard(
                                    modifier = Modifier.weight(1f),
                                    containerColor = if (totalCardOutstanding > 0) palette.spendContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentPadding = 12.dp,
                                ) {
                                    Text(
                                        "Card outstanding",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (totalCardOutstanding > 0) palette.spend else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        formatINR(totalCardOutstanding),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalCardOutstanding > 0) palette.spend else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "${cards.size} card${if (cards.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (sources.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No banks or cards yet",
                                body = "Add your bank accounts and credit cards to track balances and outstanding amounts automatically.",
                            )
                        }
                    }

                    items(sources, key = { it.id }) { source ->
                        val owed = sourceDisplayedBalance(source, state.expenses)
                        val isCreditCard = source.kind == Constants.SOURCE_CREDIT_CARD
                        val last = state.expenses
                            .filter { it.sourceId == source.id }
                            .maxByOrNull { it.timestamp }
                        val lastLine = if (last != null) {
                            val label = if (last.isSpend()) "Spent" else if (last.type == "income") "Received" else "Moved"
                            "$label ${formatINR(last.amount)} · ${formatTimestamp(last.timestamp)}"
                        } else {
                            "No transactions yet"
                        }
                        KashCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { navigator.push(AccountDetailScreen(sourceId = source.id)) },
                            contentPadding = 14.dp,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            source.icon.ifBlank { if (isCreditCard) "💳" else "🏦" },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Column {
                                            Text(
                                                source.displayLabel(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                if (isCreditCard) "Credit Card" else "Bank Account",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    IconButton(
                                        modifier = Modifier.size(36.dp),
                                        onClick = { startEdit(source) },
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            if (isCreditCard) "Outstanding amount" else "Available balance",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            formatINR(if (isCreditCard) owed.coerceAtLeast(0.0) else owed, alwaysShowDecimals = owed % 1.0 != 0.0),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCreditCard) {
                                                if (owed > 0) palette.spend else MaterialTheme.colorScheme.onSurface
                                            } else {
                                                palette.income
                                            },
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        lastLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "Statement & Passbook ›",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        PrimaryButton(
                            text = "Add bank or card",
                            onClick = {
                                editing = null
                                resetDraft(Constants.SOURCE_BANK)
                                showEditor = true
                            },
                        )
                    }
                }
            }
        }

        if (showEditor) {
            val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showEditor = false },
                sheetState = editorSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (editing != null) "Edit ${editing!!.kindLabel().lowercase()}" else "Add bank or card",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == Constants.SOURCE_BANK,
                            onClick = {
                                kind = Constants.SOURCE_BANK
                                if (editing == null) {
                                    icon = defaultSourceIcon(kind)
                                    color = defaultSourceColor(kind)
                                }
                            },
                            label = { Text("Bank") },
                        )
                        FilterChip(
                            selected = kind == Constants.SOURCE_CREDIT_CARD,
                            onClick = {
                                kind = Constants.SOURCE_CREDIT_CARD
                                if (editing == null) {
                                    icon = defaultSourceIcon(kind)
                                    color = defaultSourceColor(kind)
                                }
                            },
                            label = { Text("Credit Card") },
                        )
                    }
                    VaultOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (kind == Constants.SOURCE_CREDIT_CARD) "Card name" else "Bank name") },
                        placeholder = {
                            Text(if (kind == Constants.SOURCE_CREDIT_CARD) "HDFC Millennia" else "HDFC Bank")
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    VaultOutlinedTextField(
                        value = last4,
                        onValueChange = { last4 = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Last 4 digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                    VaultOutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(if (kind == Constants.SOURCE_CREDIT_CARD) "Outstanding now" else "Balance now")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (kind == Constants.SOURCE_CREDIT_CARD) {
                        PaidFromChips(
                            sources = banks,
                            selectedId = payFromId,
                            onSelect = { payFromId = it },
                            label = "Pay bill from",
                        )
                    }
                    VaultOutlinedTextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("SMS / UPI tags") },
                        placeholder = { Text("hdfc, upi, gpay") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text("Icon", style = MaterialTheme.typography.labelSmall)
                    SourceEmojiRow(selected = icon, onSelect = { icon = it })
                    Text("Color", style = MaterialTheme.typography.labelSmall)
                    SourceColorRow(selected = color, onSelect = { color = it })
                    Spacer(modifier = Modifier.height(4.dp))
                    PrimaryButton(
                        text = if (editing != null) "Save" else "Add",
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) return@PrimaryButton
                            val digits = com.kashvault.domain.normalizeLast4(last4)
                            val tags = keywordsFromText(keywordsText).ifEmpty {
                                defaultSourceTags(trimmed, digits)
                            }
                            val displayed = parseMoneyInput(balanceText).let { if (it.isFinite()) it else 0.0 }
                            val current = editing
                            if (current != null) {
                                val opening = openingBalanceForDisplayed(
                                    current.copy(kind = kind),
                                    state.expenses,
                                    displayed,
                                )
                                vm.savePaymentSource(
                                    current.copy(
                                        name = trimmed,
                                        kind = kind,
                                        last4 = digits,
                                        icon = icon,
                                        color = color,
                                        keywords = tags,
                                        openingBalance = opening,
                                        statementDueDate = if (kind == Constants.SOURCE_CREDIT_CARD) current.statementDueDate else null,
                                        payFromSourceId = if (kind == Constants.SOURCE_CREDIT_CARD) payFromId else null,
                                    ),
                                )
                            } else {
                                vm.addPaymentSource(
                                    trimmed,
                                    kind,
                                    last4,
                                    icon,
                                    color,
                                    tags,
                                    displayed,
                                    statementDueDate = null,
                                    payFromSourceId = if (kind == Constants.SOURCE_CREDIT_CARD) payFromId else null,
                                )
                            }
                            showEditor = false
                            editing = null
                            resetDraft()
                        },
                    )
                    if (editing != null) {
                        SecondaryButton(
                            text = "Remove",
                            onClick = {
                                pendingDelete = editing
                                showEditor = false
                            },
                        )
                    }
                }
            }
        }

        pendingDelete?.let { source ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Remove ${source.name}?") },
                text = { Text("Past transactions keep their history. New ones will not offer this ${source.kindLabel().lowercase()}.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deletePaymentSource(source.id)
                        pendingDelete = null
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun SourceEmojiRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("🏦", "💳", "💵", "💰", "🏧", "🏛️", "💎", "🪙") + CATEGORY_EMOJI_OPTIONS.take(16)
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.distinct().take(8).forEach { emoji ->
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(emoji) },
                color = if (selected == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        emoji,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SourceColorRow(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        (CATEGORY_COLOR_OPTIONS + selected).distinct().forEach { value ->
            val optionColor = Color(android.graphics.Color.parseColor(if (value.startsWith("#")) value else "#$value"))
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(value) },
                color = optionColor,
                shape = CircleShape,
            ) {
                if (selected.equals(value, ignoreCase = true)) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(24.dp),
                            tint = if (optionColor.luminance() > 0.5f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}
