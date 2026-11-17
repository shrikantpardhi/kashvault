package com.kashvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import com.kashvault.domain.EmergencyFund
import com.kashvault.domain.PaymentSource
import com.kashvault.domain.SavingsGoal
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.ItemActionRow
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.components.ProgressBar
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.util.formatINR
import com.kashvault.util.parseMoneyInput
import kotlinx.datetime.Clock

object GoalsScreen : Screen {
    override val key = "goals"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()

        var editingEmergency by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<SavingsGoal?>(null) }
        var pendingDelete by remember { mutableStateOf<SavingsGoal?>(null) }
        var addToGoalId by remember { mutableStateOf<String?>(null) }
        var addEmergency by remember { mutableStateOf(false) }
        var addAmount by remember { mutableStateOf("") }
        var addSourceId by remember { mutableStateOf(state.lastPaymentSourceId) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "Goals") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if (editingEmergency) {
                        EmergencyEditor(
                            fund = state.emergencyFund,
                            onSave = { vm.saveEmergencyFund(it); editingEmergency = false },
                            onCancel = { editingEmergency = false },
                        )
                    } else {
                        KashCard {
                            Text("Emergency fund", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatINR(state.emergencyFund.currentSaved)} · ${state.emergencyFund.targetMonths} month target",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { editingEmergency = true }) { Text("Edit") }
                                TextButton(onClick = { addEmergency = true; addToGoalId = null }) { Text("Add money") }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { editing = blankGoal() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add savings goal") }
                }

                if (state.savingsGoals.isEmpty() && editing == null) {
                    item {
                        Text("No savings goals yet. Name a target so Wealth can show progress.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (editing != null && editing!!.id.isBlank()) {
                    item {
                        GoalEditor(
                            draft = editing!!,
                            onChange = { editing = it },
                            onSave = {
                                val draft = editing ?: return@GoalEditor
                                if (draft.name.isBlank() || draft.target <= 0) return@GoalEditor
                                vm.saveSavingsGoals(state.savingsGoals + draft.copy(id = "${Clock.System.now().toEpochMilliseconds()}"))
                                editing = null
                            },
                            onCancel = { editing = null },
                            onDelete = null,
                        )
                    }
                }

                items(state.savingsGoals, key = { it.id }) { goal ->
                    if (editing?.id == goal.id) {
                        GoalEditor(
                            draft = editing!!,
                            onChange = { editing = it },
                            onSave = {
                                val draft = editing ?: return@GoalEditor
                                if (draft.name.isBlank() || draft.target <= 0) return@GoalEditor
                                vm.saveSavingsGoals(state.savingsGoals.map { if (it.id == draft.id) draft else it })
                                editing = null
                            },
                            onCancel = { editing = null },
                            onDelete = { pendingDelete = goal },
                        )
                    } else {
                        val pct = if (goal.target > 0) (goal.current / goal.target * 100) else 0.0
                        KashCard {
                            Text(goal.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${formatINR(goal.current)} of ${formatINR(goal.target)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ProgressBar(pct)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ItemActionRow(
                                    onEdit = { editing = goal },
                                    onDelete = { pendingDelete = goal },
                                )
                                TextButton(onClick = { addToGoalId = goal.id; addEmergency = false }) { Text("Add money") }
                            }
                        }
                    }
                }

                if (addEmergency || addToGoalId != null) {
                    item {
                        ContributeCard(
                            title = if (addEmergency) {
                                "Move cash into emergency"
                            } else {
                                "Move cash into ${state.savingsGoals.find { it.id == addToGoalId }?.name}"
                            },
                            amount = addAmount,
                            onAmount = { addAmount = it },
                            sources = state.paymentSources,
                            sourceId = addSourceId,
                            onSource = { addSourceId = it },
                            onTransfer = {
                                val amt = parseMoneyInput(addAmount)
                                if (!amt.isFinite() || amt <= 0) return@ContributeCard
                                if (addEmergency) vm.contributeToEmergency(amt, addSourceId)
                                else addToGoalId?.let { vm.contributeToGoal(it, amt, addSourceId) }
                                addEmergency = false
                                addToGoalId = null
                                addAmount = ""
                            },
                            onCancel = {
                                addEmergency = false
                                addToGoalId = null
                                addAmount = ""
                            },
                        )
                    }
                }
            }
        }

        pendingDelete?.let { goal ->
            ConfirmDeleteDialog(
                title = "Delete ${goal.name}?",
                onConfirm = {
                    vm.saveSavingsGoals(state.savingsGoals.filter { it.id != goal.id })
                    if (editing?.id == goal.id) editing = null
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

private fun blankGoal() = SavingsGoal(id = "", name = "", target = 0.0, current = 0.0)

@Composable
private fun EmergencyEditor(
    fund: EmergencyFund,
    onSave: (EmergencyFund) -> Unit,
    onCancel: () -> Unit,
) {
    var months by remember(fund) { mutableStateOf(fund.targetMonths.toString()) }
    var monthlyNeed by remember(fund) {
        mutableStateOf(if (fund.monthlyRequirement == 0.0) "" else moneyField(fund.monthlyRequirement))
    }
    var saved by remember(fund) { mutableStateOf(moneyField(fund.currentSaved)) }
    KashCard {
        Text("Edit emergency fund", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            months,
            { months = it },
            label = { Text("Target months") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            monthlyNeed,
            { monthlyNeed = it },
            label = { Text("Monthly requirement") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            saved,
            { saved = it },
            label = { Text("Currently saved") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val m = months.toIntOrNull() ?: fund.targetMonths
                val need = parseMoneyInput(monthlyNeed).let { if (it.isFinite()) it else 0.0 }
                val current = parseMoneyInput(saved).let { if (it.isFinite()) it else fund.currentSaved }
                onSave(fund.copy(targetMonths = m, monthlyRequirement = need, currentSaved = current))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun GoalEditor(
    draft: SavingsGoal,
    onChange: (SavingsGoal) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    KashCard {
        Text(if (draft.id.isBlank()) "New savings goal" else "Edit ${draft.name}", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            draft.name,
            { onChange(draft.copy(name = it)) },
            label = { Text("Goal name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            moneyField(draft.target),
            {
                val amt = parseMoneyInput(it)
                onChange(draft.copy(target = if (amt.isFinite()) amt else 0.0))
            },
            label = { Text("Target") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            moneyField(draft.current),
            {
                val amt = parseMoneyInput(it)
                onChange(draft.copy(current = if (amt.isFinite()) amt else 0.0))
            },
            label = { Text("Currently saved") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.name.isNotBlank() && draft.target > 0,
        ) { Text("Save") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ContributeCard(
    title: String,
    amount: String,
    onAmount: (String) -> Unit,
    sources: List<PaymentSource>,
    sourceId: String?,
    onSource: (String) -> Unit,
    onTransfer: () -> Unit,
    onCancel: () -> Unit,
) {
    KashCard {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            amount,
            onAmount,
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(8.dp))
        PaidFromChips(sources = sources, selectedId = sourceId, onSelect = onSource)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) { Text("Transfer") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
