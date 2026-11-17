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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.domain.Insurance
import com.kashvault.domain.Investment
import com.kashvault.domain.PaymentSource
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.DatePickerField
import com.kashvault.ui.components.ItemActionRow
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.util.formatINR
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.todayLocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

object RecurringScreen : Screen {
    override val key = "recurring"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()

        var editingInv by remember { mutableStateOf<Investment?>(null) }
        var editingIns by remember { mutableStateOf<Insurance?>(null) }
        var pendingDeleteInv by remember { mutableStateOf<Investment?>(null) }
        var pendingDeleteIns by remember { mutableStateOf<Insurance?>(null) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "Recurring") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                editingIns = null
                                editingInv = blankInvestment(state.lastPaymentSourceId)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Add SIP") }
                        Button(
                            onClick = {
                                editingInv = null
                                editingIns = blankInsurance(state.lastPaymentSourceId)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Add insurance") }
                    }
                }

                if (state.investments.isEmpty() && state.insurances.isEmpty() && editingInv == null && editingIns == null) {
                    item {
                        Text(
                            "No SIPs or insurance yet. Add one so it can post on the due date.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (editingInv != null && editingInv!!.id.isBlank()) {
                    item {
                        InvestmentEditor(
                            draft = editingInv!!,
                            sources = state.paymentSources,
                            onChange = { editingInv = it },
                            onSave = {
                                val draft = editingInv ?: return@InvestmentEditor
                                if (draft.name.isBlank() || draft.amount <= 0) return@InvestmentEditor
                                vm.saveInvestments(state.investments + draft.copy(id = newId()))
                                editingInv = null
                            },
                            onCancel = { editingInv = null },
                            onDelete = null,
                        )
                    }
                }

                items(state.investments, key = { it.id }) { inv ->
                    if (editingInv?.id == inv.id) {
                        InvestmentEditor(
                            draft = editingInv!!,
                            sources = state.paymentSources,
                            onChange = { editingInv = it },
                            onSave = {
                                val draft = editingInv ?: return@InvestmentEditor
                                if (draft.name.isBlank() || draft.amount <= 0) return@InvestmentEditor
                                vm.saveInvestments(state.investments.map { if (it.id == draft.id) draft else it })
                                editingInv = null
                            },
                            onCancel = { editingInv = null },
                            onDelete = { pendingDeleteInv = inv },
                        )
                    } else {
                        KashCard {
                            Text("${inv.iconOrType()} ${inv.name}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatINR(inv.amount)} · ${inv.type}" + (inv.dueDate?.let { " · due $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ItemActionRow(
                                onEdit = {
                                    editingIns = null
                                    editingInv = inv
                                },
                                onDelete = { pendingDeleteInv = inv },
                            )
                        }
                    }
                }

                if (editingIns != null && editingIns!!.id.isBlank()) {
                    item {
                        InsuranceEditor(
                            draft = editingIns!!,
                            sources = state.paymentSources,
                            onChange = { editingIns = it },
                            onSave = {
                                val draft = editingIns ?: return@InsuranceEditor
                                if (draft.name.isBlank() || draft.premium <= 0) return@InsuranceEditor
                                vm.saveInsurances(state.insurances + draft.copy(id = newId()))
                                editingIns = null
                            },
                            onCancel = { editingIns = null },
                            onDelete = null,
                        )
                    }
                }

                items(state.insurances, key = { it.id }) { ins ->
                    if (editingIns?.id == ins.id) {
                        InsuranceEditor(
                            draft = editingIns!!,
                            sources = state.paymentSources,
                            onChange = { editingIns = it },
                            onSave = {
                                val draft = editingIns ?: return@InsuranceEditor
                                if (draft.name.isBlank() || draft.premium <= 0) return@InsuranceEditor
                                vm.saveInsurances(state.insurances.map { if (it.id == draft.id) draft else it })
                                editingIns = null
                            },
                            onCancel = { editingIns = null },
                            onDelete = { pendingDeleteIns = ins },
                        )
                    } else {
                        KashCard {
                            Text("🛡️ ${ins.name}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Premium ${formatINR(ins.premium)} · ${ins.frequency} · due ${ins.dueDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ItemActionRow(
                                onEdit = {
                                    editingInv = null
                                    editingIns = ins
                                },
                                onDelete = { pendingDeleteIns = ins },
                            )
                        }
                    }
                }
            }
        }

        pendingDeleteInv?.let { inv ->
            ConfirmDeleteDialog(
                title = "Delete ${inv.name}?",
                onConfirm = {
                    vm.saveInvestments(state.investments.filter { it.id != inv.id })
                    if (editingInv?.id == inv.id) editingInv = null
                    pendingDeleteInv = null
                },
                onDismiss = { pendingDeleteInv = null },
            )
        }
        pendingDeleteIns?.let { ins ->
            ConfirmDeleteDialog(
                title = "Delete ${ins.name}?",
                onConfirm = {
                    vm.saveInsurances(state.insurances.filter { it.id != ins.id })
                    if (editingIns?.id == ins.id) editingIns = null
                    pendingDeleteIns = null
                },
                onDismiss = { pendingDeleteIns = null },
            )
        }
    }
}

private fun newId(): String = "${Clock.System.now().toEpochMilliseconds()}"

private fun blankInvestment(sourceId: String?) = Investment(
    id = "",
    name = "",
    amount = 0.0,
    type = "SIP",
    frequency = "Monthly",
    timestamp = Clock.System.now().toString(),
    dueDate = todayLocalDate().toString(),
    sourceId = sourceId,
)

private fun blankInsurance(sourceId: String?) = Insurance(
    id = "",
    name = "",
    premium = 0.0,
    frequency = "Yearly",
    dueDate = todayLocalDate().toString(),
    sourceId = sourceId,
)

private fun Investment.iconOrType(): String = when (type) {
    "SIP" -> "💎"
    "Stocks" -> "📈"
    else -> "💰"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvestmentEditor(
    draft: Investment,
    sources: List<PaymentSource>,
    onChange: (Investment) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    KashCard {
        Text(if (draft.id.isBlank()) "New SIP / investment" else "Edit ${draft.name}", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            draft.name,
            { onChange(draft.copy(name = it)) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            moneyField(draft.amount),
            {
                val amt = parseMoneyInput(it)
                onChange(draft.copy(amount = if (amt.isFinite()) amt else 0.0))
            },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SIP", "Stocks", "Lumpsum").forEach { t ->
                FilterChip(
                    selected = draft.type == t,
                    onClick = {
                        onChange(
                            draft.copy(
                                type = t,
                                frequency = if (t == "SIP") "Monthly" else "OneTime",
                            ),
                        )
                    },
                    label = { Text(t) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Due date",
            date = parseLocalDateOrNull(draft.dueDate) ?: todayLocalDate(),
            onDate = { date: LocalDate -> onChange(draft.copy(dueDate = date.toString())) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        PaidFromChips(sources = sources, selectedId = draft.sourceId, onSelect = { onChange(draft.copy(sourceId = it)) })
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = draft.name.isNotBlank() && draft.amount > 0) {
            Text("Save")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsuranceEditor(
    draft: Insurance,
    sources: List<PaymentSource>,
    onChange: (Insurance) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    KashCard {
        Text(if (draft.id.isBlank()) "New insurance" else "Edit ${draft.name}", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            draft.name,
            { onChange(draft.copy(name = it)) },
            label = { Text("Policy name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            moneyField(draft.premium),
            {
                val amt = parseMoneyInput(it)
                onChange(draft.copy(premium = if (amt.isFinite()) amt else 0.0))
            },
            label = { Text("Premium") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Monthly", "Quarterly", "Yearly").forEach { freq ->
                FilterChip(
                    selected = draft.frequency == freq,
                    onClick = { onChange(draft.copy(frequency = freq)) },
                    label = { Text(freq) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "Due date",
            date = parseLocalDateOrNull(draft.dueDate) ?: todayLocalDate(),
            onDate = { date -> onChange(draft.copy(dueDate = date.toString())) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        PaidFromChips(sources = sources, selectedId = draft.sourceId, onSelect = { onChange(draft.copy(sourceId = it)) })
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = draft.name.isNotBlank() && draft.premium > 0) {
            Text("Save")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("This removes it from Wealth. Past transactions stay in history.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun moneyField(amount: Double): String =
    if (amount == 0.0) "" else if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
