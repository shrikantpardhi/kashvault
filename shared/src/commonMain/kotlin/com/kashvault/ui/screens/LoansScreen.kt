package com.kashvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.kashvault.domain.Constants
import com.kashvault.domain.Loan
import com.kashvault.domain.PaymentSource
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.DatePickerField
import com.kashvault.ui.components.ItemActionRow
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PaidFromChips
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.util.formatDisplayDate
import com.kashvault.util.formatINR
import com.kashvault.util.formatMonthYear
import com.kashvault.util.getLoanOutstandingTotal
import com.kashvault.util.loanFirstEmiDate
import com.kashvault.util.loanIsClosed
import com.kashvault.util.loanIsReadyToSave
import com.kashvault.util.nextDueDate
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.parseMoneyInput
import com.kashvault.util.preparedLoanForSave
import com.kashvault.util.remainingEmis
import com.kashvault.util.roundCurrency
import com.kashvault.util.todayLocalDate
import kotlinx.datetime.Clock

object LoansScreen : Screen {
    override val key = "loans"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()

        var editing by remember { mutableStateOf<Loan?>(null) }
        var pendingDelete by remember { mutableStateOf<Loan?>(null) }
        var prepaying by remember { mutableStateOf<Loan?>(null) }
        val banks = state.paymentSources.filter { it.kind != Constants.SOURCE_CREDIT_CARD }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "Loans") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Button(
                        onClick = { editing = blankLoan(state.lastPaymentSourceId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add loan") }
                }

                if (state.loans.isEmpty() && editing == null) {
                    item {
                        Text(
                            "No loans yet. Add the EMI your lender already set — KashVault will put it on the due timeline.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (editing != null && editing!!.id.isBlank()) {
                    item {
                        LoanEditor(
                            draft = editing!!,
                            sources = state.paymentSources,
                            onChange = { editing = it },
                            onSave = {
                                val draft = editing ?: return@LoanEditor
                                if (!loanIsReadyToSave(draft)) return@LoanEditor
                                vm.saveLoans(
                                    state.loans + preparedLoanForSave(draft).copy(
                                        id = "${Clock.System.now().toEpochMilliseconds()}",
                                    ),
                                )
                                editing = null
                            },
                            onCancel = { editing = null },
                            onDelete = null,
                        )
                    }
                }

                items(state.loans.sortedBy { loanIsClosed(it) }, key = { it.id }) { loan ->
                    if (editing?.id == loan.id) {
                        LoanEditor(
                            draft = editing!!,
                            sources = state.paymentSources,
                            onChange = { editing = it },
                            onSave = {
                                val draft = editing ?: return@LoanEditor
                                if (!loanIsReadyToSave(draft)) return@LoanEditor
                                val saved = preparedLoanForSave(draft)
                                vm.saveLoans(state.loans.map { if (it.id == saved.id) saved else it })
                                editing = null
                            },
                            onCancel = { editing = null },
                            onDelete = { pendingDelete = loan },
                        )
                    } else {
                        KashCard {
                            Text(loan.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                loanSummaryLine(loan),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!loanIsClosed(loan)) {
                                TextButton(onClick = { prepaying = loan }) { Text("Prepay") }
                            }
                            ItemActionRow(
                                onEdit = { editing = loan },
                                onDelete = { pendingDelete = loan },
                            )
                        }
                    }
                }
            }
        }

        pendingDelete?.let { loan ->
            ConfirmDeleteDialog(
                title = "Delete ${loan.name}?",
                onConfirm = {
                    vm.saveLoans(state.loans.filter { it.id != loan.id })
                    if (editing?.id == loan.id) editing = null
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }

        prepaying?.let { loan ->
            LoanPrepayDialog(
                loan = loan,
                banks = banks,
                defaultSourceId = loan.sourceId ?: state.lastPaymentSourceId,
                onConfirm = { amount, sourceId ->
                    vm.prepayLoan(loan.id, amount, sourceId)
                    prepaying = null
                },
                onDismiss = { prepaying = null },
            )
        }
    }
}

private fun blankLoan(sourceId: String?) = Loan(
    id = "",
    name = "",
    totalAmount = 0.0,
    tenureMonths = 12,
    emi = 0.0,
    remainingAmount = 0.0,
    dueDate = todayLocalDate().toString(),
    startDate = todayLocalDate().toString(),
    sourceId = sourceId,
)

private fun loanSummaryLine(loan: Loan): String {
    if (loanIsClosed(loan)) {
        val prepaid = loan.totalPrepaid ?: 0.0
        return buildString {
            append("Closed")
            if (prepaid > 0) append(" · prepaid ${formatINR(prepaid)}")
        }
    }
    val left = remainingEmis(loan)
    val tenure = loan.tenureMonths
    val prepaid = loan.totalPrepaid ?: 0.0
    return buildString {
        append("EMI ${formatINR(loan.emi)}")
        if (tenure != null && tenure > 0) append(" · $left of $tenure mo left")
        append(" · outstanding ${formatINR(getLoanOutstandingTotal(loan))}")
        if (prepaid > 0) append(" · prepaid ${formatINR(prepaid)}")
        val first = loanFirstEmiDate(loan.dueDate, loan.startDate)
        val next = nextDueDate(
            dueDate = loan.dueDate,
            frequency = "Monthly",
            lastProcessedMonth = loan.lastProcessedMonth,
            today = todayLocalDate(),
            notBefore = first,
        )
        if (next != null) append(" · next ${formatDisplayDate(next)}")
        first?.let { append(" · starts ${formatMonthYear(it)}") }
    }
}

@Composable
private fun LoanPrepayDialog(
    loan: Loan,
    banks: List<PaymentSource>,
    defaultSourceId: String?,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val outstanding = getLoanOutstandingTotal(loan)
    var amountText by remember(loan.id) { mutableStateOf("") }
    var sourceId by remember(loan.id) {
        mutableStateOf(defaultSourceId?.takeIf { id -> banks.any { it.id == id } } ?: banks.firstOrNull()?.id)
    }
    val parsed = parseMoneyInput(amountText)
    val amount = if (parsed.isFinite() && parsed > 0) roundCurrency(minOf(parsed, outstanding)) else 0.0
    val closes = amount > 0 && amount >= outstanding
    val leftover = roundCurrency((outstanding - amount).coerceAtLeast(0.0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prepay ${loan.name}") },
        text = {
            Column {
                Text(
                    "Outstanding ${formatINR(outstanding)}. Prepay any amount from a bank. Paying the full leftover closes the loan.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                VaultOutlinedTextField(
                    amountText,
                    { amountText = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (loan.emi > 0 && loan.emi < outstanding) {
                        TextButton(onClick = { amountText = moneyField(loan.emi) }) { Text("This EMI") }
                    }
                    TextButton(onClick = { amountText = moneyField(outstanding) }) { Text("Close loan") }
                }
                if (amount > 0) {
                    Text(
                        if (closes) "This closes the loan."
                        else "Outstanding becomes ${formatINR(leftover)} · ${remainingEmis(loan.copy(remainingAmount = leftover))} mo left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (banks.isEmpty()) {
                    Text("Add a bank first so this prepay can come from cash.")
                } else {
                    PaidFromChips(
                        sources = banks,
                        selectedId = sourceId,
                        onSelect = { sourceId = it },
                        label = "Pay from",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val from = sourceId ?: return@TextButton
                    onConfirm(amount, from)
                },
                enabled = amount > 0 && !sourceId.isNullOrBlank() && banks.isNotEmpty(),
            ) { Text(if (closes) "Close loan" else "Prepay") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LoanEditor(
    draft: Loan,
    sources: List<PaymentSource>,
    onChange: (Loan) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val outstandingHint = if (draft.remainingAmount <= 0 && draft.emi > 0 && (draft.tenureMonths ?: 0) > 0) {
        "Leave blank to use EMI × remaining months"
    } else {
        "What you still owe. Prepay any amount to cut this and close early."
    }
    KashCard {
        Text(if (draft.id.isBlank()) "New loan" else "Edit ${draft.name}", style = MaterialTheme.typography.titleSmall)
        Text(
            "No interest calculator — enter the EMI and tenure your lender already gave you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            draft.name,
            { onChange(draft.copy(name = it)) },
            label = { Text("Loan name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            moneyField(draft.emi),
            {
                val amt = parseMoneyInput(it)
                onChange(draft.copy(emi = if (amt.isFinite()) amt else 0.0))
            },
            label = { Text("EMI") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            draft.tenureMonths?.toString().orEmpty(),
            {
                val t = it.toIntOrNull() ?: 0
                onChange(draft.copy(tenureMonths = t.takeIf { months -> months > 0 }))
            },
            label = { Text("Tenure (months)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        VaultOutlinedTextField(
            moneyField(draft.remainingAmount),
            {
                val p = parseMoneyInput(it)
                onChange(draft.copy(remainingAmount = if (p.isFinite()) p else 0.0))
            },
            label = { Text("Outstanding") },
            supportingText = { Text(outstandingHint) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "EMI due date",
            date = parseLocalDateOrNull(draft.dueDate) ?: todayLocalDate(),
            onDate = {
                val previousDue = parseLocalDateOrNull(draft.dueDate)
                val start = parseLocalDateOrNull(draft.startDate)
                val keepStart = start != null && previousDue != null &&
                    (start.year != previousDue.year || start.monthNumber != previousDue.monthNumber)
                onChange(
                    draft.copy(
                        dueDate = it.toString(),
                        startDate = if (keepStart) draft.startDate else it.toString(),
                    ),
                )
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        DatePickerField(
            label = "EMI start month",
            date = parseLocalDateOrNull(draft.startDate)
                ?: parseLocalDateOrNull(draft.dueDate)
                ?: todayLocalDate(),
            onDate = { onChange(draft.copy(startDate = it.toString())) },
        )
        Text(
            "If the first EMI is next month, it still shows on this month’s due list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PaidFromChips(
            sources = sources,
            selectedId = draft.sourceId,
            onSelect = { onChange(draft.copy(sourceId = it)) },
            label = "EMI paid from",
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = loanIsReadyToSave(draft),
        ) { Text("Save") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
