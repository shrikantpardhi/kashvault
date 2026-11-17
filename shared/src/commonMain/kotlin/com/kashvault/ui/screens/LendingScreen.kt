package com.kashvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.BorrowLend
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.displayLabel
import com.kashvault.domain.kindLabel
import com.kashvault.ui.components.PickerOption
import com.kashvault.ui.components.SearchablePickerField
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.DatePickerField
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.MetricTile
import com.kashvault.ui.components.MoneyText
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.components.PersonAutocompleteField
import com.kashvault.ui.components.PersonAvatar
import com.kashvault.ui.components.SectionHeader
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.rememberSingleClick
import com.kashvault.ui.theme.ButtonHeight
import com.kashvault.ui.components.BankVoucherSheet
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.TransactionFingerprints
import com.kashvault.util.canCreatePerson
import com.kashvault.util.formatINR
import com.kashvault.util.formatTimestamp
import com.kashvault.util.getLendingTotals
import com.kashvault.util.matchingPeople
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.sourceDisplayedBalance
import com.kashvault.util.timestampOnDate
import com.kashvault.util.todayLocalDate
import kotlinx.datetime.LocalDate

object LendingScreen : Screen {
    override val key = "lending"

    @Composable
    override fun Content() {
        KashVaultScaffold(currentScreen = this, title = "Lending") { LendingPane(it) }
    }
}

@Composable
internal fun LendingPane(modifier: Modifier = Modifier) {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val lending = remember(state.borrowLend, state.people) {
            getLendingTotals(state.borrowLend, state.people)
        }
        val navigator = LocalNavigator.currentOrThrow
        val colors = vaultPalette()

        var showAdd by remember { mutableStateOf(false) }
        var person by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var selectedPersonId by remember { mutableStateOf<String?>(null) }
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("lend") }
        var entryDate by remember { mutableStateOf(todayLocalDate()) }
        var selectedSourceId by remember { mutableStateOf<String?>(null) }

        if (isLoading) {
            LoadingScreen()
            return
        }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(
                            label = "To receive",
                            value = formatINR(lending.totalReceived),
                            caption = "Open with people",
                            modifier = Modifier.weight(1f),
                            accent = colors.income,
                            containerColor = colors.incomeContainer,
                        )
                        MetricTile(
                            label = "To give",
                            value = formatINR(lending.toGive),
                            caption = "Open balance",
                            modifier = Modifier.weight(1f),
                            accent = colors.spend,
                            containerColor = colors.spendContainer,
                        )
                    }
                }
                item {
                    Button(onClick = { showAdd = !showAdd }, modifier = Modifier.fillMaxWidth().height(ButtonHeight)) {
                        Text(if (showAdd) "Close" else "Record money")
                    }
                }
                if (showAdd) {
                    item {
                        LendingEditor(
                            person = person,
                            onPerson = {
                                person = it
                                selectedPersonId = null
                            },
                            people = state.people,
                            selectedPersonId = selectedPersonId,
                            onSelectPerson = { picked ->
                                selectedPersonId = picked.id
                                person = picked.name
                                phone = picked.phone.orEmpty()
                            },
                            phone = phone,
                            onPhone = { phone = it },
                            amount = amount,
                            onAmount = { amount = it },
                            note = note,
                            onNote = { note = it },
                            type = type,
                            onType = { type = it },
                            date = entryDate,
                            onDate = { entryDate = it },
                            sources = state.paymentSources,
                            selectedSourceId = selectedSourceId,
                            onSelectSource = { selectedSourceId = it },
                            onSave = {
                                val amt = parseMoneyInput(amount)
                                if (!amt.isFinite() || person.isBlank()) return@LendingEditor
                                vm.addBorrowLend(
                                    person = person.trim(),
                                    amount = amt,
                                    type = type,
                                    note = note.ifBlank { null },
                                    personId = selectedPersonId,
                                    phone = phone.ifBlank { null },
                                    timestamp = timestampOnDate(entryDate),
                                    sourceId = selectedSourceId,
                                )
                                showAdd = false
                                person = ""
                                phone = ""
                                selectedPersonId = null
                                amount = ""
                                note = ""
                                selectedSourceId = null
                                entryDate = todayLocalDate()
                            },
                            onCreatePerson = { name ->
                                val created = vm.ensurePerson(name, phone.ifBlank { null })
                                selectedPersonId = created.id
                                person = created.name
                                phone = created.phone.orEmpty()
                            },
                        )
                    }
                }

                item { SectionHeader("People") }

                val directory = buildList {
                    val seen = mutableSetOf<String>()
                    lending.byPerson.forEach { net ->
                        val key = net.personId ?: net.person.lowercase()
                        seen += key
                        add(net)
                    }
                    state.people.forEach { row ->
                        val key = row.id
                        if (key !in seen && lending.byPerson.none { it.person.equals(row.name, true) }) {
                            add(com.kashvault.util.PersonLendingNet(row.name, 0.0, 0.0, 0.0, row.id))
                        }
                    }
                }.sortedBy { it.person }

                if (directory.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No person logs yet",
                            body = "Keep track of money you've lent to friends, colleagues, or borrowed. Tap below to start.",
                            icon = "🤝",
                            actionLabel = "Record first transaction",
                            onAction = { showAdd = true },
                        )
                    }
                } else {
                    items(
                        directory.distinctBy { it.personId ?: it.person.lowercase() },
                        key = { it.personId ?: it.person.lowercase() },
                    ) { personNet ->
                        KashCard(onClick = { navigator.push(PersonLendingScreen(personNet.person, personNet.personId)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PersonAvatar(personNet.person)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(personNet.person, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        when {
                                            personNet.net > 0 -> "Owes you"
                                            personNet.net < 0 -> "You owe"
                                            else -> "Settled"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MoneyText(
                                    kotlin.math.abs(personNet.net),
                                    type = if (personNet.net < 0) "expense" else if (personNet.net > 0) "income" else "neutral",
                                )
                            }
                        }
                    }
                }
            }
}

data class PersonLendingScreen(val person: String, val personId: String? = null) : Screen {
    override val key get() = "lending-person-${personId ?: person}"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val logs = state.borrowLend.filter { item ->
            (personId != null && item.personId == personId) ||
                item.person.equals(person, ignoreCase = true)
        }.sortedByDescending { it.timestamp }
        val open = logs.filter { !it.settled }
        val given = open.filter { it.type != "borrow" }.sumOf { it.amount }
        val received = open.filter { it.type == "borrow" }.sumOf { it.amount }
        val net = given - received

        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("lend") }
        var entryDate by remember { mutableStateOf(todayLocalDate()) }
        var settled by remember { mutableStateOf(false) }
        var showAdd by remember { mutableStateOf(false) }
        var editingLog by remember { mutableStateOf<BorrowLend?>(null) }
        var selectedSourceId by remember { mutableStateOf<String?>(null) }
        var repayOpen by remember { mutableStateOf(false) }
        var repayLogId by remember { mutableStateOf<String?>(null) }
        var repaySourceId by remember { mutableStateOf(state.lastPaymentSourceId) }
        var voucherExpense by remember { mutableStateOf<com.kashvault.domain.Expense?>(null) }
        val clicks = rememberSingleClick()
        val record = state.people.find { it.id == personId } ?: state.people.find { it.name.equals(person, true) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = person) { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    KashCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PersonAvatar(person, size = 56)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    when {
                                        net > 0 -> "Owes you ${formatINR(net)}"
                                        net < 0 -> "You owe ${formatINR(-net)}"
                                        else -> "All settled"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Given ${formatINR(given)} · received ${formatINR(received)}" +
                                        (record?.phone?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                editingLog = null
                                amount = ""
                                note = ""
                                type = "lend"
                                entryDate = todayLocalDate()
                                settled = false
                                selectedSourceId = null
                                showAdd = true
                            }, modifier = Modifier.weight(1f).height(ButtonHeight)) { Text("Add entry") }
                            OutlinedButton(
                                onClick = { repayLogId = null; repayOpen = true },
                                modifier = Modifier.weight(1f).height(ButtonHeight),
                                enabled = kotlin.math.abs(net) > 0,
                            ) { Text("Repay") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.settlePerson(person, personId) },
                                modifier = Modifier.weight(1f).height(ButtonHeight),
                                enabled = open.isNotEmpty(),
                            ) { Text("Settle all") }
                        }
                    }
                }
                if (showAdd) {
                    item {
                        LendingEditor(
                            person = person,
                            onPerson = {},
                            people = emptyList(),
                            selectedPersonId = personId,
                            onSelectPerson = {},
                            onCreatePerson = {},
                            phone = record?.phone.orEmpty(),
                            onPhone = {},
                            amount = amount,
                            onAmount = { amount = it },
                            note = note,
                            onNote = { note = it },
                            type = type,
                            onType = { type = it },
                            date = entryDate,
                            onDate = { entryDate = it },
                            settled = if (editingLog != null) settled else null,
                            onSettled = { settled = it },
                            lockPerson = true,
                            sources = state.paymentSources,
                            selectedSourceId = selectedSourceId,
                            onSelectSource = { selectedSourceId = it },
                            onSave = {
                                val amt = parseMoneyInput(amount)
                                if (!amt.isFinite()) return@LendingEditor
                                val existing = editingLog
                                if (existing != null) {
                                    vm.updateBorrowLend(
                                        id = existing.id,
                                        person = person,
                                        amount = amt,
                                        type = type,
                                        note = note.ifBlank { null },
                                        personId = personId,
                                        timestamp = timestampOnDate(entryDate),
                                        settled = settled,
                                        sourceId = selectedSourceId,
                                    )
                                } else {
                                    vm.addBorrowLend(
                                        person = person,
                                        amount = amt,
                                        type = type,
                                        note = note.ifBlank { null },
                                        personId = personId,
                                        timestamp = timestampOnDate(entryDate),
                                        sourceId = selectedSourceId,
                                    )
                                }
                                showAdd = false
                                editingLog = null
                                amount = ""
                                note = ""
                                selectedSourceId = null
                                entryDate = todayLocalDate()
                                settled = false
                            },
                        )
                    }
                }
                item { SectionHeader("Log") }
                if (logs.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No entries with $person",
                            body = "Add a lend or borrow entry to maintain a clear tally.",
                            icon = "📝",
                            actionLabel = "Add entry",
                            onAction = { showAdd = true },
                        )
                    }
                } else {
                    items(logs, key = { it.id }) { item ->
                        val linked = state.expenses.find { it.id == "${item.id}_mov" || it.dueEntityId == item.id }
                        val account = linked?.sourceId?.let { sId -> state.paymentSources.find { it.id == sId } }
                        PersonLogCard(
                            item = item,
                            accountLabel = account?.displayLabel(),
                            onViewVoucher = linked?.let { exp -> { voucherExpense = exp } },
                            onRepay = { repayLogId = item.id; repayOpen = true },
                            onSettle = { vm.settleBorrowLend(item.id) },
                            onEdit = {
                                editingLog = item
                                amount = if (item.amount % 1.0 == 0.0) item.amount.toLong().toString() else item.amount.toString()
                                note = item.note.orEmpty()
                                type = item.type
                                entryDate = parseLocalDateOrNull(item.timestamp) ?: todayLocalDate()
                                settled = item.settled
                                selectedSourceId = linked?.sourceId
                                showAdd = true
                            },
                            onDelete = { vm.deleteBorrowLend(item.id) },
                        )
                    }
                }
            }
        }

        if (repayOpen) {
            AlertDialog(
                onDismissRequest = { repayOpen = false },
                title = { Text("Repay from which account?") },
                text = {
                    Column {
                        Text(
                            "Moves the person log and, if you pick a bank, also posts cash.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PaidFromChips(
                            sources = state.paymentSources,
                            selectedId = repaySourceId,
                            onSelect = { repaySourceId = it },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            clicks("repayCash") {
                                val logId = repayLogId
                                if (logId != null) vm.repayBorrowLend(logId, repaySourceId)
                                else vm.repayPerson(person, personId, kotlin.math.abs(net), sourceId = repaySourceId)
                                repayOpen = false
                                repayLogId = null
                            }
                        },
                    ) { Text("Repay with cash") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            clicks("repayLog") {
                                val logId = repayLogId
                                if (logId != null) vm.repayBorrowLend(logId, null)
                                else vm.repayPerson(person, personId, kotlin.math.abs(net))
                                repayOpen = false
                                repayLogId = null
                            }
                        },
                    ) { Text("Log only") }
                },
            )
        }

        voucherExpense?.let { exp ->
            BankVoucherSheet(
                voucher = TransactionFingerprints.buildTransactionReceipt(exp, state.paymentSources, state.expenses),
                onDismiss = { voucherExpense = null },
            )
        }
    }
}

@Composable
private fun PersonLogCard(
    item: BorrowLend,
    accountLabel: String? = null,
    onViewVoucher: (() -> Unit)? = null,
    onRepay: () -> Unit,
    onSettle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    KashCard(containerColor = if (item.settled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (item.type == "borrow") "You borrowed" else "You lent",
                    fontWeight = FontWeight.Medium,
                )
                if (item.note != null) {
                    Text(item.note, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    formatTimestamp(item.timestamp) + if (item.settled) " · settled" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (accountLabel != null) {
                    Text(
                        "🏦 Reflected in $accountLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        "🤝 Prior / Offline record (No account impact)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            MoneyText(item.amount, type = if (item.type == "lend") "expense" else "income")
        }
        Row {
            if (onViewVoucher != null) {
                TextButton(onClick = onViewVoucher) {
                    Text("Voucher", color = com.kashvault.ui.theme.NeoEmerald, fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(onClick = onEdit) { Text("Edit") }
            if (!item.settled) {
                TextButton(onClick = onRepay) { Text("Repay") }
                TextButton(onClick = onSettle) { Text("Settle") }
            }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
internal fun LendingEditor(
    person: String,
    onPerson: (String) -> Unit,
    amount: String,
    onAmount: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    type: String,
    onType: (String) -> Unit,
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    onSave: () -> Unit,
    lockPerson: Boolean = false,
    people: List<com.kashvault.domain.Person> = emptyList(),
    selectedPersonId: String? = null,
    onSelectPerson: (com.kashvault.domain.Person) -> Unit = {},
    phone: String = "",
    onPhone: (String) -> Unit = {},
    onCreatePerson: (String) -> Unit = {},
    settled: Boolean? = null,
    onSettled: (Boolean) -> Unit = {},
    sources: List<PaymentSource> = emptyList(),
    selectedSourceId: String? = null,
    onSelectSource: (String?) -> Unit = {},
) {
    val matches = matchingPeople(people, person)
    val showCreate = !lockPerson && canCreatePerson(people, person)
    KashCard {
        if (!lockPerson) {
            VaultOutlinedTextField(person, onPerson, label = { Text("Person") }, modifier = Modifier.fillMaxWidth())
            if (matches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    matches.forEach { row ->
                        FilterChip(
                            selected = selectedPersonId == row.id,
                            onClick = { onSelectPerson(row) },
                            label = { Text(row.name) },
                        )
                    }
                }
            }
            if (showCreate) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { onCreatePerson(person.trim()) }) {
                    Text("Create New Person")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            VaultOutlinedTextField(
                phone,
                onPhone,
                label = { Text("Phone (optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        VaultOutlinedTextField(
            amount,
            onAmount,
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(label = "Date", date = date, onDate = onDate)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(note, onNote, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "lend", onClick = { onType("lend") }, label = { Text("I lent") })
            FilterChip(selected = type == "borrow", onClick = { onType("borrow") }, label = { Text("I borrowed") })
        }
        if (settled != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !settled, onClick = { onSettled(false) }, label = { Text("Open") })
                FilterChip(selected = settled, onClick = { onSettled(true) }, label = { Text("Settled") })
            }
        }
        if (sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val currentSource = sources.find { it.id == selectedSourceId }
            val sourceLabel = if (type == "lend") "Reflect in account (Paid from)" else "Reflect in account (Received in)"
            val defaultLabel = "None / Prior (No account impact)"
            val accountOptions = listOf(PickerOption("", "None / Prior (No account impact)", "Already lent/borrowed earlier · Tracking only")) +
                sources.map { PickerOption(it.id, it.displayLabel(), it.kindLabel()) }

            SearchablePickerField(
                label = sourceLabel,
                selectedLabel = currentSource?.displayLabel() ?: defaultLabel,
                options = accountOptions,
                onSelect = { onSelectSource(it.ifBlank { null }) },
                placeholder = "Select account or keep none",
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (selectedSourceId == null) {
                    "No account movement. No settlement required — ideal if you started using today but already lent or borrowed earlier without impacting transaction integrity."
                } else {
                    "Will ${if (type == "lend") "debit" else "credit"} ${currentSource?.displayLabel()} so your bank balance stays accurate."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(ButtonHeight)) { Text("Save") }
    }
}
