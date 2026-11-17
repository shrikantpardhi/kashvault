package com.kashvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.domain.RecurringBill
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.DatePickerField
import com.kashvault.ui.components.ItemActionRow
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.components.PickerOption
import com.kashvault.ui.components.SearchablePickerField
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.util.formatINR
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.todayLocalDate
import kotlinx.datetime.Clock

object BillsScreen : Screen {
    override val key = "bills"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        var editing by remember { mutableStateOf<RecurringBill?>(null) }
        var addingIncome by remember { mutableStateOf(false) }
        var pendingDelete by remember { mutableStateOf<RecurringBill?>(null) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        val bills = state.recurringBills.filter { it.type != "income" }
        val income = state.recurringBills.filter { it.type == "income" }

        KashVaultScaffold(currentScreen = this, title = "Bills & salary") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Rent, utilities, and salary post automatically on the due date, or when you Mark paid.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                addingIncome = false
                                editing = RecurringBill(
                                    id = "",
                                    name = "",
                                    amount = 0.0,
                                    categoryId = "cat_rent",
                                    frequency = "Monthly",
                                    dueDate = todayLocalDate().toString(),
                                    sourceId = state.lastPaymentSourceId,
                                    type = "expense",
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Add bill") }
                        Button(
                            onClick = {
                                addingIncome = true
                                editing = RecurringBill(
                                    id = "",
                                    name = "Salary",
                                    amount = 0.0,
                                    categoryId = "cat_salary",
                                    frequency = "Monthly",
                                    dueDate = todayLocalDate().toString(),
                                    sourceId = state.lastPaymentSourceId,
                                    type = "income",
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Add salary") }
                    }
                }

                if (bills.isEmpty() && income.isEmpty() && editing == null) {
                    item { Text("No recurring bills yet.", style = MaterialTheme.typography.bodySmall) }
                }

                bills.forEach { bill ->
                    item {
                        if (editing?.id == bill.id && bill.id.isNotBlank()) {
                            BillEditor(
                                draft = editing!!,
                                income = false,
                                categories = state.categories,
                                sources = state.paymentSources,
                                onChange = { editing = it },
                                onSave = {
                                    val draft = editing ?: return@BillEditor
                                    vm.saveRecurringBills(state.recurringBills.map { if (it.id == draft.id) draft else it })
                                    editing = null
                                },
                                onDelete = { pendingDelete = bill },
                                onCancel = { editing = null },
                            )
                        } else {
                            KashCard {
                                Text(bill.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${formatINR(bill.amount)} · ${bill.frequency} · due ${bill.dueDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ItemActionRow(
                                    onEdit = { addingIncome = false; editing = bill },
                                    onDelete = { pendingDelete = bill },
                                )
                            }
                        }
                    }
                }
                income.forEach { bill ->
                    item {
                        if (editing?.id == bill.id && bill.id.isNotBlank()) {
                            BillEditor(
                                draft = editing!!,
                                income = true,
                                categories = state.categories,
                                sources = state.paymentSources,
                                onChange = { editing = it },
                                onSave = {
                                    val draft = editing ?: return@BillEditor
                                    vm.saveRecurringBills(state.recurringBills.map { if (it.id == draft.id) draft else it })
                                    editing = null
                                },
                                onDelete = { pendingDelete = bill },
                                onCancel = { editing = null },
                            )
                        } else {
                            KashCard {
                                Text(bill.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${formatINR(bill.amount)} in · ${bill.frequency} · ${bill.dueDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ItemActionRow(
                                    onEdit = { addingIncome = true; editing = bill },
                                    onDelete = { pendingDelete = bill },
                                )
                            }
                        }
                    }
                }

                if (editing != null && editing!!.id.isBlank()) {
                    item {
                        val draft = editing!!
                        BillEditor(
                            draft = draft,
                            income = addingIncome || draft.type == "income",
                            categories = state.categories,
                            sources = state.paymentSources,
                            onChange = { editing = it },
                            onSave = {
                                val current = editing ?: return@BillEditor
                                vm.saveRecurringBills(
                                    state.recurringBills + current.copy(id = "${Clock.System.now().toEpochMilliseconds()}"),
                                )
                                editing = null
                            },
                            onDelete = null,
                            onCancel = { editing = null },
                        )
                    }
                }
            }
        }

        pendingDelete?.let { bill ->
            ConfirmDeleteDialog(
                title = "Delete ${bill.name}?",
                onConfirm = {
                    vm.saveRecurringBills(state.recurringBills.filter { it.id != bill.id })
                    if (editing?.id == bill.id) editing = null
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillEditor(
    draft: RecurringBill,
    income: Boolean,
    categories: List<com.kashvault.domain.Category>,
    sources: List<com.kashvault.domain.PaymentSource>,
    onChange: (RecurringBill) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val typed = categories.filter { if (income) it.type == "income" else it.type != "income" }
    val selected = typed.find { it.id == draft.categoryId } ?: typed.firstOrNull()
    KashCard {
        Text(if (income) "Income" else "Bill", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            if (draft.amount == 0.0) "" else if (draft.amount % 1.0 == 0.0) draft.amount.toLong().toString() else draft.amount.toString(),
            {
                val amt = parseMoneyInput(it)
                onChange(draft.copy(amount = if (amt.isFinite()) amt else 0.0))
            },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SearchablePickerField(
            label = "Category",
            selectedLabel = selected?.let { "${it.icon} ${it.name}" } ?: "Category",
            options = typed.map { PickerOption(it.id, "${it.icon} ${it.name}") },
            onSelect = { onChange(draft.copy(categoryId = it)) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Monthly", "Quarterly", "Yearly").forEach { freq ->
                FilterChip(selected = draft.frequency == freq, onClick = { onChange(draft.copy(frequency = freq)) }, label = { Text(freq) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Due date",
            date = parseLocalDateOrNull(draft.dueDate) ?: todayLocalDate(),
            onDate = { onChange(draft.copy(dueDate = it.toString())) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        PaidFromChips(
            sources = sources,
            selectedId = draft.sourceId,
            onSelect = { onChange(draft.copy(sourceId = it)) },
            label = if (income) "Received in" else "Paid from",
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = draft.name.isNotBlank() && draft.amount > 0) {
            Text("Save")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
