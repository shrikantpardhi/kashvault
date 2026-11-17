package com.kashvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kashvault.ui.rememberSingleClick
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.Constants
import com.kashvault.domain.displayLabel
import com.kashvault.domain.isSpend
import com.kashvault.domain.kindLabel
import com.kashvault.domain.lastUsedExpenseCategoryId
import com.kashvault.domain.preferredExpenseCategoryId
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.DatePickerField
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PickerOption
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.SearchablePickerField
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.navigation.TabsScreen
import com.kashvault.ui.theme.ButtonHeight
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.canCreatePerson
import com.kashvault.util.formatINR
import com.kashvault.util.matchingPeople
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.sourceDisplayedBalance
import com.kashvault.util.timestampOnDate
import com.kashvault.util.todayLocalDate

data class AddExpenseScreen(
    val expenseId: String? = null,
    val duplicate: Boolean = false,
) : Screen {
    override val key: String
        get() = "add-${expenseId ?: "new"}-${if (duplicate) "dup" else "edit"}"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val clicks = rememberSingleClick()

        if (isLoading) {
            LoadingScreen()
            return
        }

        val existing = expenseId?.let { id -> state.expenses.find { it.id == id } }
        val editing = existing != null && !duplicate
        val seed = existing
        val title = when {
            editing -> "Edit"
            duplicate -> "Duplicate"
            else -> "Add"
        }

        var amountText by remember {
            mutableStateOf(seed?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
        }
        var note by remember { mutableStateOf(seed?.note.orEmpty()) }
        var selectedCategoryId by remember {
            mutableStateOf(
                seed?.categoryId ?: preferredExpenseCategoryId(
                    state.categories,
                    lastUsedId = lastUsedExpenseCategoryId(state.expenses),
                    type = seed?.type ?: "expense",
                ),
            )
        }
        var selectedSourceId by remember { mutableStateOf(seed?.sourceId ?: state.lastPaymentSourceId) }
        var selectedDestinationId by remember { mutableStateOf(seed?.destinationId) }
        var type by remember { mutableStateOf(seed?.type ?: "expense") }
        var showMagicDialog by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var date by remember { mutableStateOf(seed?.timestamp?.let { parseLocalDateOrNull(it) } ?: todayLocalDate()) }
        var personName by remember { mutableStateOf("") }
        var personPhone by remember { mutableStateOf("") }
        var selectedPersonId by remember { mutableStateOf<String?>(null) }
        var lendType by remember { mutableStateOf("lend") }

        val filteredCategories = state.categories.filter {
            type == Constants.TYPE_TRANSFER || it.type == type || (type == "expense" && it.type != "income")
        }
        val selectedCategory = filteredCategories.find { it.id == selectedCategoryId }
            ?: filteredCategories.find {
                it.id == preferredExpenseCategoryId(filteredCategories, type = type)
            }
        val destOptions = buildList {
            state.paymentSources.filter { it.id != selectedSourceId }.forEach { source ->
                add(PickerOption(source.id, source.displayLabel(), source.kindLabel()))
            }
            add(PickerOption(Constants.DEST_EMERGENCY, "🛟 Emergency fund", "Savings"))
            state.savingsGoals.forEach { goal ->
                add(PickerOption(Constants.goalDestination(goal.id), "🎯 ${goal.name}", "Goal"))
            }
        }
        val selectedDest = destOptions.find { it.id == selectedDestinationId }
        val paidFrom = state.paymentSources.find { it.id == selectedSourceId }
        val parsedAmount = parseMoneyInput(amountText)
        val palette = vaultPalette()
        val amountColor = when (type) {
            "income" -> palette.income
            "expense" -> palette.spend
            Constants.TYPE_TRANSFER -> MaterialTheme.colorScheme.primary
            Constants.TYPE_LENDING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurface
        }

        fun finish() {
            if (navigator.canPop) navigator.pop() else navigator.replaceAll(TabsScreen)
        }

        fun save() {
            val amount = parseMoneyInput(amountText)
            if (!amount.isFinite() || amount <= 0) {
                error = "Enter a valid amount"
                return
            }
            if (type == Constants.TYPE_LENDING) {
                if (personName.isBlank()) {
                    error = "Enter a person"
                    return
                }
                vm.addBorrowLend(
                    person = personName.trim(),
                    amount = amount,
                    type = lendType,
                    note = note.ifBlank { null },
                    personId = selectedPersonId,
                    phone = personPhone.ifBlank { null },
                    timestamp = timestampOnDate(date),
                    sourceId = selectedSourceId?.takeIf { it.isNotBlank() },
                )
                finish()
                return
            }
            if (state.paymentSources.isNotEmpty() && paidFrom == null) {
                error = when (type) {
                    "income" -> "Choose which bank or card received this"
                    Constants.TYPE_TRANSFER -> "Choose which bank sends this"
                    else -> "Choose which bank or card paid this"
                }
                return
            }
            if (type == Constants.TYPE_TRANSFER) {
                val destinationId = selectedDestinationId
                if (destinationId.isNullOrBlank()) {
                    error = "Choose where this money goes"
                    return
                }
                if (destinationId == selectedSourceId) {
                    error = "Pick a different destination"
                    return
                }
                val transferCategory = state.categories.find { it.id == "cat_wealth" }?.id
                    ?: state.categories.firstOrNull { it.type != "income" }?.id
                    ?: selectedCategoryId
                if (transferCategory.isBlank()) {
                    error = "Choose a category"
                    return
                }
                val saved = vm.addExpense(
                    amount = amount,
                    categoryId = transferCategory,
                    note = note.ifBlank { null },
                    type = Constants.TYPE_TRANSFER,
                    sourceId = selectedSourceId,
                    timestamp = timestampOnDate(date),
                    destinationId = destinationId,
                    id = if (editing) existing?.id else null,
                )
                if (saved) finish() else error = "Could not save this transaction"
                return
            }
            val categoryId = selectedCategory?.id ?: selectedCategoryId
            if (categoryId.isBlank()) {
                error = "Choose a category"
                return
            }
            val saved = vm.addExpense(
                amount = amount,
                categoryId = categoryId,
                note = note.ifBlank { null },
                type = type,
                sourceId = selectedSourceId,
                timestamp = timestampOnDate(date),
                id = if (editing) existing?.id else null,
            )
            if (saved) finish() else error = "Could not save this transaction"
        }

        KashVaultScaffold(currentScreen = this, title = title) { modifier ->
            Column(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = type == "expense",
                                    onClick = { type = "expense" },
                                    label = { Text("Expense") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.spendContainer,
                                        selectedLabelColor = palette.spend,
                                    ),
                                    border = if (type == "expense") {
                                        BorderStroke(1.dp, palette.spend.copy(alpha = 0.5f))
                                    } else null,
                                )
                                FilterChip(
                                    selected = type == "income",
                                    onClick = { type = "income" },
                                    label = { Text("Income") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.incomeContainer,
                                        selectedLabelColor = palette.income,
                                    ),
                                    border = if (type == "income") {
                                        BorderStroke(1.dp, palette.income.copy(alpha = 0.5f))
                                    } else null,
                                )
                                FilterChip(
                                    selected = type == Constants.TYPE_TRANSFER,
                                    onClick = { type = Constants.TYPE_TRANSFER },
                                    label = { Text("Transfer") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    border = if (type == Constants.TYPE_TRANSFER) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    } else null,
                                )
                                FilterChip(
                                    selected = type == Constants.TYPE_LENDING,
                                    onClick = { type = Constants.TYPE_LENDING },
                                    label = { Text("Lending") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.tertiary,
                                    ),
                                    border = if (type == Constants.TYPE_LENDING) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                                    } else null,
                                )
                            }

                            Surface(
                                onClick = { showMagicDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        "Magic Log",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        VaultOutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            textStyle = MaterialTheme.typography.displaySmall.copy(
                                color = amountColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            prefix = {
                                Text(
                                    Constants.RUPEE_SIGN,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = amountColor,
                                )
                            },
                            placeholder = {
                                Text(
                                    "0",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                            compact = false,
                        )
                    }

                    if (type == Constants.TYPE_LENDING) {
                        item {
                            val matches = matchingPeople(state.people, personName)
                            Column {
                            VaultOutlinedTextField(
                                value = personName,
                                onValueChange = {
                                    personName = it
                                    selectedPersonId = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Person") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                            )
                            if (matches.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    matches.forEach { row ->
                                        FilterChip(
                                            selected = selectedPersonId == row.id,
                                            onClick = {
                                                selectedPersonId = row.id
                                                personName = row.name
                                                personPhone = row.phone.orEmpty()
                                            },
                                            label = { Text(row.name) },
                                        )
                                    }
                                }
                            }
                            if (canCreatePerson(state.people, personName)) {
                                TextButton(
                                    onClick = {
                                        val created = vm.ensurePerson(personName.trim(), personPhone.ifBlank { null })
                                        selectedPersonId = created.id
                                        personName = created.name
                                        personPhone = created.phone.orEmpty()
                                    },
                                ) { Text("Create New Person") }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            VaultOutlinedTextField(
                                value = personPhone,
                                onValueChange = { personPhone = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Phone (optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = lendType == "lend", onClick = { lendType = "lend" }, label = { Text("I lent") })
                                FilterChip(selected = lendType == "borrow", onClick = { lendType = "borrow" }, label = { Text("I borrowed") })
                            }
                            }
                        }
                    }

                    if (type != Constants.TYPE_TRANSFER && type != Constants.TYPE_LENDING) {
                        item {
                            SearchablePickerField(
                                label = "Category",
                                selectedLabel = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "Choose a category",
                                options = filteredCategories.map { PickerOption(it.id, "${it.icon} ${it.name}", it.type) },
                                onSelect = { selectedCategoryId = it },
                                placeholder = "Search categories",
                            )
                        }
                    }

                    item {
                        Column {
                        if (state.paymentSources.isEmpty()) {
                            if (type != Constants.TYPE_LENDING) {
                                Text(
                                    "Add a bank or card so this amount has a home.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { navigator.push(PaymentSourcesScreen) }, modifier = Modifier.height(ButtonHeight)) {
                                    Text("Add a bank or card")
                                }
                            }
                        } else {
                            val sourceLabel = when (type) {
                                "income" -> "Account"
                                Constants.TYPE_TRANSFER -> "From"
                                Constants.TYPE_LENDING -> if (lendType == "lend") "Paid from (Optional)" else "Received in (Optional)"
                                else -> "Account"
                            }
                            val defaultLabel = if (type == Constants.TYPE_LENDING) "None / Historical (No account impact)" else "Choose a bank or card"
                            val accountOptions = if (type == Constants.TYPE_LENDING) {
                                listOf(PickerOption("", "None / Historical (No account impact)", "Tracking only (no bank movement)")) +
                                    state.paymentSources.map { source ->
                                        val subtitle = if (source.kind == Constants.SOURCE_CREDIT_CARD) {
                                            val owed = sourceDisplayedBalance(source, state.expenses)
                                            if (owed > 0) "Credit card · ${formatINR(owed)} outstanding" else source.kindLabel()
                                        } else {
                                            source.kindLabel()
                                        }
                                        PickerOption(source.id, source.displayLabel(), subtitle)
                                    }
                            } else {
                                state.paymentSources.map { source ->
                                    val subtitle = if (source.kind == Constants.SOURCE_CREDIT_CARD) {
                                        val owed = sourceDisplayedBalance(source, state.expenses)
                                        if (owed > 0) "Credit card · ${formatINR(owed)} outstanding" else source.kindLabel()
                                    } else {
                                        source.kindLabel()
                                    }
                                    PickerOption(source.id, source.displayLabel(), subtitle)
                                }
                            }
                            SearchablePickerField(
                                label = sourceLabel,
                                selectedLabel = paidFrom?.displayLabel() ?: defaultLabel,
                                options = accountOptions,
                                onSelect = {
                                    selectedSourceId = it.ifBlank { null }
                                    if (selectedDestinationId == selectedSourceId) selectedDestinationId = null
                                },
                                placeholder = "Search banks and cards",
                            )
                            if (type == Constants.TYPE_LENDING) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (paidFrom == null) {
                                        "No account movement. No settlement required — ideal if you started using today but already lent or borrowed earlier without impacting transaction integrity."
                                    } else {
                                        "Will ${if (lendType == "lend") "debit" else "credit"} ${paidFrom.displayLabel()} so your account balance stays accurate."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (type == "expense" && paidFrom?.kind == Constants.SOURCE_CREDIT_CARD) {
                                val current = sourceDisplayedBalance(paidFrom, state.expenses)
                                val previousOnThisCard =
                                    if (editing && existing?.sourceId == paidFrom.id && existing.isSpend()) existing.amount else 0.0
                                val nextAmount = if (parsedAmount.isFinite() && parsedAmount > 0) parsedAmount else 0.0
                                val projected = current - previousOnThisCard + nextAmount
                                Text(
                                    if (nextAmount > 0) {
                                        "Outstanding on ${paidFrom.name} will become ${formatINR(projected)}."
                                    } else {
                                        "Outstanding on ${paidFrom.name} is ${formatINR(current)}."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        }
                    }

                    if (type == Constants.TYPE_TRANSFER) {
                        item {
                            SearchablePickerField(
                                label = "To",
                                selectedLabel = selectedDest?.label ?: "Goal, emergency, bank, or card",
                                options = destOptions,
                                onSelect = { selectedDestinationId = it },
                                placeholder = "Search destination",
                            )
                        }
                    }

                    item {
                        DatePickerField(label = "Date", date = date, onDate = { date = it })
                    }

                    item {
                        VaultOutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Note") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                    }

                    if (error != null) {
                        item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryButton(
                            text = if (editing) "Save" else "Save",
                            onClick = { save() },
                        )
                    }
                }
            }

            if (showMagicDialog) {
                var dialogMagicText by remember { mutableStateOf("") }
                var dialogLoading by remember { mutableStateOf(false) }
                var dialogError by remember { mutableStateOf<String?>(null) }
                var aiDisclosureAccepted by remember { mutableStateOf(false) }
                val hasGeminiKey = state.userProfile?.geminiApiKey?.isNotBlank() == true

                AlertDialog(
                    onDismissRequest = {
                        if (!dialogLoading) showMagicDialog = false
                    },
                    icon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                    title = {
                        Text(
                            "Magic Log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Describe your transaction in natural language. When you continue, Google Gemini receives this text and the available category and payment-account labels.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (hasGeminiKey) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = aiDisclosureAccepted,
                                        onCheckedChange = { aiDisclosureAccepted = it },
                                    )
                                    Text(
                                        "I agree to send this information to Google for parsing.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            VaultOutlinedTextField(
                                value = dialogMagicText,
                                onValueChange = {
                                    dialogMagicText = it
                                    dialogError = null
                                    aiDisclosureAccepted = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g., Spent ₹350 at Starbucks with HDFC card") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(16.dp),
                            )
                            if (!hasGeminiKey) {
                                Text(
                                    "No Gemini API key set. Add one in Settings → AI settings for full AI support.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (dialogError != null) {
                                Text(
                                    dialogError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (dialogMagicText.isBlank() || dialogLoading) return@Button
                                dialogLoading = true
                                dialogError = null
                                vm.magicLog(dialogMagicText) { success ->
                                    dialogLoading = false
                                    if (success) {
                                        showMagicDialog = false
                                        finish()
                                    } else {
                                        dialogError = if (state.userProfile?.geminiApiKey.isNullOrBlank()) {
                                            "Could not parse. Add a Gemini API key in Settings → AI settings."
                                        } else {
                                            "Gemini could not parse that transaction. Please check the text or API key."
                                        }
                                    }
                                }
                            },
                            enabled = dialogMagicText.isNotBlank() && !dialogLoading && hasGeminiKey && aiDisclosureAccepted,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (dialogLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Parsing…")
                            } else {
                                Text("Fill with AI")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showMagicDialog = false },
                            enabled = !dialogLoading,
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}
