package com.kashvault.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.Constants
import com.kashvault.domain.Expense
import com.kashvault.domain.displayLabel
import com.kashvault.domain.isIncome
import com.kashvault.domain.isSpend
import com.kashvault.domain.isTransfer
import com.kashvault.ui.components.BankVoucherSheet
import com.kashvault.ui.components.CategorySpendingBar
import com.kashvault.ui.components.TransactionFingerprintBanner
import com.kashvault.ui.components.TransactionFingerprintDialog
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.FilterIconButton
import com.kashvault.ui.components.FilterRow
import com.kashvault.ui.components.FilterSection
import com.kashvault.ui.components.FilterSheet
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PickerOption
import com.kashvault.ui.components.SearchablePickerField
import com.kashvault.ui.components.TransactionRow
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.haptics.platformHapticTick
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.NeoCyan
import com.kashvault.ui.theme.NeoEmerald
import com.kashvault.ui.theme.hexToColor
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.TransactionFingerprints
import com.kashvault.util.formatINR
import com.kashvault.util.formatTimestamp
import com.kashvault.util.getFilteredExpenses
import com.kashvault.util.parseMoneyInput

object HistoryScreen : Screen {
    override val key = "history"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val palette = vaultPalette()

        var filter by remember { mutableStateOf("monthly") }
        var typeFilter by remember { mutableStateOf("all") }
        var sourceFilter by remember { mutableStateOf<String?>(null) }
        var categoryFilter by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var splitting by remember { mutableStateOf<Expense?>(null) }
        var showFilters by remember { mutableStateOf(false) }
        var showCategoryBreakdown by remember { mutableStateOf(false) }
        var voucherExpense by remember { mutableStateOf<Expense?>(null) }
        var proofExpense by remember { mutableStateOf<Expense?>(null) }
        var showAuditDialog by remember { mutableStateOf(false) }
        val fingerprints = remember(state.expenses) { TransactionFingerprints.fingerprintLedger(state.expenses) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        val filtered = when (filter) {
            "all" -> state.expenses
            else -> getFilteredExpenses(state.expenses, filter)
        }.filter { sourceFilter == null || it.sourceId == sourceFilter }
            .filter { categoryFilter == null || it.categoryId == categoryFilter }
            .filter { typeFilter == "all" || (it.type ?: "expense") == typeFilter }
            .filter {
                val needle = query.trim().lowercase()
                if (needle.isEmpty()) true
                else {
                    val category = state.categories.find { cat -> cat.id == it.categoryId }
                    val source = state.paymentSources.find { src -> src.id == it.sourceId }
                    listOfNotNull(it.note, category?.name, source?.name).any { bit -> bit.lowercase().contains(needle) }
                }
            }
            .sortedByDescending { it.timestamp }

        val periodOptions = listOf(
            "daily" to "Today",
            "weekly" to "Week",
            "monthly" to "Month",
            "all" to "All Time",
        )
        val typeOptions = listOf(
            "all" to "All Types",
            "expense" to "Spends",
            "income" to "Income",
            Constants.TYPE_TRANSFER to "Transfers",
        )
        val activeFilterCount = listOf(
            filter != "monthly",
            typeFilter != "all",
            sourceFilter != null,
            categoryFilter != null,
            query.isNotBlank(),
        ).count { it }

        val totalSpent = filtered.filter { it.isSpend() }.sumOf { it.amount }
        val totalIncome = filtered.filter { it.isIncome() }.sumOf { it.amount }

        // Category breakdown calculation for filtered spends
        val categoryBreakdowns = remember(filtered, state.categories) {
            val spends = filtered.filter { it.isSpend() }
            val spendSum = spends.sumOf { it.amount }
            if (spendSum <= 0.0) emptyList()
            else {
                spends.groupBy { it.categoryId }
                    .mapNotNull { (catId, exps) ->
                        val cat = state.categories.find { it.id == catId } ?: return@mapNotNull null
                        val sum = exps.sumOf { it.amount }
                        val pct = (sum / spendSum) * 100.0
                        Triple(cat, sum, pct)
                    }
                    .sortedByDescending { it.second }
            }
        }

        KashVaultScaffold(
            currentScreen = this,
            title = "History",
        ) { modifier ->
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        TransactionFingerprintBanner(
                            fingerprintCount = fingerprints.totalTransactions,
                            onClickAudit = { showAuditDialog = true },
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VaultOutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search note, category, account...") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        FilterIconButton(activeCount = activeFilterCount, onClick = { showFilters = true })
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            )

                            // Horizontal Quick Filter Chips Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                periodOptions.forEach { (id, label) ->
                                    FilterChip(
                                        selected = filter == id,
                                        onClick = { filter = id },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                                typeOptions.filter { it.first != "all" }.forEach { (id, label) ->
                                    FilterChip(
                                        selected = typeFilter == id,
                                        onClick = { typeFilter = if (typeFilter == id) "all" else id },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                                if (activeFilterCount > 0) {
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            filter = "monthly"
                                            typeFilter = "all"
                                            sourceFilter = null
                                            categoryFilter = null
                                            query = ""
                                        },
                                        label = { Text("Reset", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                            selectedLabelColor = MaterialTheme.colorScheme.error,
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }

                            // Summary Strip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        if (totalSpent > 0 || totalIncome == 0.0) {
                                            Column {
                                                Text("Spent", style = MaterialTheme.typography.labelSmall, color = palette.spend)
                                                Text(formatINR(totalSpent), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.spend)
                                            }
                                        }
                                        if (totalIncome > 0) {
                                            Column {
                                                Text("Income", style = MaterialTheme.typography.labelSmall, color = palette.income)
                                                Text(formatINR(totalIncome), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.income)
                                            }
                                        }
                                    }
                                    Text(
                                        "${filtered.size} transaction${if (filtered.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Category Spending Breakdown Section (Collapsible)
                    if (categoryBreakdowns.isNotEmpty() && (typeFilter == "all" || typeFilter == "expense")) {
                        item {
                            KashCard(contentPadding = 12.dp) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Spending by category",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    TextButton(
                                        onClick = { showCategoryBreakdown = !showCategoryBreakdown },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(if (showCategoryBreakdown) "Collapse" else "View all (${categoryBreakdowns.size})")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val displayBreakdowns = if (showCategoryBreakdown) categoryBreakdowns else categoryBreakdowns.take(3)
                                displayBreakdowns.forEach { (cat, amount, pct) ->
                                    CategorySpendingBar(
                                        name = cat.name,
                                        icon = cat.icon,
                                        amount = amount,
                                        percent = pct,
                                        color = hexToColor(cat.color),
                                        onClick = {
                                            categoryFilter = if (categoryFilter == cat.id) null else cat.id
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (activeFilterCount > 0) "No matching transactions" else "No transactions yet",
                                body = if (activeFilterCount > 0) "Try changing your search term, time period, or category filters." else "Record your expenses, bills, or incomes to see them here.",
                                icon = if (activeFilterCount > 0) "🔍" else "💳",
                                actionLabel = if (activeFilterCount > 0) "Reset filters" else "Add transaction",
                                onAction = {
                                    if (activeFilterCount > 0) {
                                        filter = "monthly"
                                        typeFilter = "all"
                                        sourceFilter = null
                                        categoryFilter = null
                                        query = ""
                                    } else {
                                        navigator.push(AddExpenseScreen())
                                    }
                                },
                            )
                        }
                    } else {
                        items(filtered.distinctBy { it.id }, key = { it.id }) { expense ->
                            val category = state.categories.find { it.id == expense.categoryId }
                            val source = state.paymentSources.find { it.id == expense.sourceId }
                            val destination = state.paymentSources.find { it.id == expense.destinationId }
                            val isTransfer = expense.isTransfer()
                            val destName = destination?.displayLabel() ?: expense.destinationId?.removePrefix("goal:")?.let { "Goal: $it" } ?: "Transfer"
                            val title = when {
                                isTransfer -> "Transfer to $destName"
                                expense.note?.isNotBlank() == true -> expense.note.trim()
                                else -> category?.name ?: "Transaction"
                            }
                            val subtitle = when {
                                isTransfer -> {
                                    val fromName = source?.displayLabel() ?: "Account"
                                    "$fromName ➔ $destName · ${formatTimestamp(expense.timestamp)}"
                                }
                                else -> {
                                    listOfNotNull(
                                        source?.displayLabel(),
                                        category?.name.takeIf { expense.note?.isNotBlank() == true },
                                        formatTimestamp(expense.timestamp),
                                    ).joinToString(" · ")
                                }
                            }
                            KashCard {
                                TransactionRow(
                                    icon = if (isTransfer) "⇄" else (category?.icon ?: "₹"),
                                    title = title,
                                    subtitle = subtitle,
                                    amount = expense.amount,
                                    type = expense.type ?: "expense",
                                    onClick = { navigator.push(AddExpenseScreen(expenseId = expense.id)) },
                                    chipColor = if (isTransfer) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else hexToColor(category?.color.orEmpty()).copy(alpha = 0.28f),
                                    chipLabel = if (isTransfer) "Transfer" else (category?.name.takeIf { expense.note?.isNotBlank() == true }),
                                )
                                Row {
                                    TextButton(onClick = {
                                        navigator.push(AddExpenseScreen(expenseId = expense.id))
                                    }) { Text("Edit") }
                                    TextButton(onClick = {
                                        navigator.push(AddExpenseScreen(expenseId = expense.id, duplicate = true))
                                    }) { Text("Duplicate") }
                                    if (expense.isSpend()) {
                                        TextButton(onClick = { splitting = expense }) { Text("Split") }
                                    }
                                    TextButton(onClick = { vm.deleteExpense(expense.id) }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
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
            }
        }

        FilterSheet(
            visible = showFilters,
            onDismiss = { showFilters = false },
            onReset = {
                filter = "monthly"
                typeFilter = "all"
                sourceFilter = null
                categoryFilter = null
            },
        ) {
            FilterSection("Period") {
                FilterRow(options = periodOptions, selected = filter, onSelect = { filter = it })
            }
            FilterSection("Type") {
                FilterRow(options = typeOptions, selected = typeFilter, onSelect = { typeFilter = it })
            }
            if (state.categories.isNotEmpty()) {
                FilterSection("Category") {
                    SearchablePickerField(
                        label = "Category",
                        selectedLabel = state.categories.find { it.id == categoryFilter }?.let { "${it.icon} ${it.name}" }
                            ?: "All categories",
                        options = listOf(PickerOption("all", "All categories")) + state.categories.map {
                            PickerOption(it.id, "${it.icon} ${it.name}", it.type)
                        },
                        onSelect = { categoryFilter = it.takeUnless { id -> id == "all" } },
                    )
                }
            }
            if (state.paymentSources.isNotEmpty()) {
                FilterSection("Account") {
                    SearchablePickerField(
                        label = "Account",
                        selectedLabel = state.paymentSources.find { it.id == sourceFilter }?.displayLabel()
                            ?: "All accounts",
                        options = listOf(PickerOption("all", "All accounts")) + state.paymentSources.map {
                            PickerOption(it.id, it.displayLabel(), it.kind)
                        },
                        onSelect = { sourceFilter = it.takeUnless { id -> id == "all" } },
                    )
                }
            }
        }

        splitting?.let { expense ->
            SplitExpenseDialog(
                expense = expense,
                categories = state.categories.filter { it.type != "income" },
                onDismiss = { splitting = null },
                onSplit = { firstCategoryId, firstAmount, secondCategoryId ->
                    if (vm.splitExpense(expense.id, firstCategoryId, firstAmount, secondCategoryId)) {
                        splitting = null
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

        if (showAuditDialog) {
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            var copied by remember { mutableStateOf(false) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAuditDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = null,
                            tint = NeoEmerald,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ledger Fingerprints", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            fingerprints.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "COMBINED SHA-256 DIGEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeoCyan,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = fingerprints.combinedDigest,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("• ${fingerprints.totalTransactions} current-record fingerprints included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• This digest does not detect edits or verify authenticity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(fingerprints.combinedDigest))
                            copied = true
                        }) {
                            Text(if (copied) "Copied!" else "Copy Digest")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Button(onClick = { showAuditDialog = false }) {
                            Text("Done")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SplitExpenseDialog(
    expense: Expense,
    categories: List<com.kashvault.domain.Category>,
    onDismiss: () -> Unit,
    onSplit: (firstCategoryId: String, firstAmount: Double, secondCategoryId: String) -> Unit,
) {
    val spendCategories = categories.ifEmpty { emptyList() }
    var firstCategoryId by remember(expense.id) { mutableStateOf(expense.categoryId) }
    var secondCategoryId by remember(expense.id) {
        mutableStateOf(spendCategories.firstOrNull { it.id != expense.categoryId }?.id ?: expense.categoryId)
    }
    var firstAmountText by remember(expense.id) {
        mutableStateOf(
            if (expense.amount % 1.0 == 0.0) (expense.amount / 2).toLong().toString()
            else (expense.amount / 2).toString(),
        )
    }
    val firstAmount = parseMoneyInput(firstAmountText)
    val remainder = if (firstAmount.isFinite()) expense.amount - firstAmount else Double.NaN
    val firstLabel = spendCategories.find { it.id == firstCategoryId }?.let { "${it.icon} ${it.name}" } ?: "First category"
    val secondLabel = spendCategories.find { it.id == secondCategoryId }?.let { "${it.icon} ${it.name}" } ?: "Second category"
    val canSplit = firstAmount.isFinite() && firstAmount > 0 && remainder.isFinite() && remainder > 0 &&
        firstCategoryId.isNotBlank() && secondCategoryId.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${formatINR(expense.amount)}") },
        text = {
            Column {
                Text(
                    "Replace this spend with two rows that add up to the original amount.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SearchablePickerField(
                    label = "First category",
                    selectedLabel = firstLabel,
                    options = spendCategories.map { PickerOption(it.id, "${it.icon} ${it.name}") },
                    onSelect = { firstCategoryId = it },
                )
                Spacer(modifier = Modifier.height(8.dp))
                VaultOutlinedTextField(
                    value = firstAmountText,
                    onValueChange = { firstAmountText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("First amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SearchablePickerField(
                    label = "Second category",
                    selectedLabel = secondLabel,
                    options = spendCategories.map { PickerOption(it.id, "${it.icon} ${it.name}") },
                    onSelect = { secondCategoryId = it },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (remainder.isFinite() && remainder > 0) "Second amount: ${formatINR(remainder)}"
                    else "Enter a valid first amount less than ${formatINR(expense.amount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainder.isFinite() && remainder > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSplit(firstCategoryId, firstAmount, secondCategoryId) },
                enabled = canSplit,
            ) { Text("Split") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
