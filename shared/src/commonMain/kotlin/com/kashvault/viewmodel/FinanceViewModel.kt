package com.kashvault.viewmodel

import com.kashvault.ai.GeminiClient
import com.kashvault.ai.geminiFailureMessage
import com.kashvault.ai.normalizeGeminiModel
import com.kashvault.ai.resolveGeminiModel
import com.kashvault.auth.GoogleAuth
import com.kashvault.auth.GoogleAuthException
import com.kashvault.auth.GoogleSignInDataPlan
import com.kashvault.auth.hasUserFinanceData
import com.kashvault.auth.overlayGoogleSession
import com.kashvault.auth.planGoogleSignIn
import com.kashvault.auth.snapshotAccountKey
import com.kashvault.auth.vaultAccountKey
import com.kashvault.auth.OAuthTokens
import com.kashvault.auth.StoredOAuth
import com.kashvault.auth.humanizeGoogleSignInError
import com.kashvault.auth.isValidVaultPin
import com.kashvault.backup.BackupCancelledException
import com.kashvault.backup.CloudBackupFile
import com.kashvault.backup.DatabaseBackupException
import com.kashvault.backup.DriveVaultClient
import com.kashvault.backup.PlatformBackupActions
import com.kashvault.backup.VAULT_BACKUP_PREFIX
import com.kashvault.backup.VAULT_BACKUP_SUFFIX
import com.kashvault.backup.assertSqliteDatabase
import com.kashvault.backup.createPlatformBackupActions
import com.kashvault.backup.deleteFileIfExists
import com.kashvault.backup.writeFileBytes
import com.kashvault.inbox.CardBillMatch
import com.kashvault.inbox.InboxCapture
import com.kashvault.inbox.createInboxCapture
import com.kashvault.reminder.InboxReminderScheduler
import com.kashvault.reminder.createInboxReminderScheduler
import com.kashvault.reminder.formatReminderClock
import com.kashvault.inbox.defaultSourceTags
import com.kashvault.inbox.matchInboxToCardBill
import com.kashvault.data.FinanceRepository
import com.kashvault.domain.*
import com.kashvault.profile.createPlatformImagePicker
import com.kashvault.profile.PlatformImagePicker
import com.kashvault.ui.theme.THEME_LIGHT
import com.kashvault.ui.theme.normalizeTheme
import com.kashvault.ui.theme.themeLabel
import com.kashvault.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val googleAuth: GoogleAuth,
    geminiClientParam: GeminiClient? = null,
    private val driveClient: DriveVaultClient = DriveVaultClient(),
    private val fileActions: PlatformBackupActions = createPlatformBackupActions(),
    private val imagePicker: PlatformImagePicker = createPlatformImagePicker(),
    private val inboxCapture: InboxCapture = createInboxCapture(),
    private val reminderScheduler: InboxReminderScheduler = createInboxReminderScheduler(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val geminiClient: GeminiClient by lazy { geminiClientParam ?: GeminiClient() }

    private val _state = MutableStateFlow(FinanceSnapshot())
    val state: StateFlow<FinanceSnapshot> = _state.asStateFlow()

    private val _themePreference = MutableStateFlow(THEME_LIGHT)
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _advisorReply = MutableStateFlow<String?>(null)
    val advisorReply: StateFlow<String?> = _advisorReply.asStateFlow()

    private val _advisorMessages = MutableStateFlow<List<AdvisorChatMessage>>(emptyList())
    val advisorMessages: StateFlow<List<AdvisorChatMessage>> = _advisorMessages.asStateFlow()

    private val _isAskingAdvisor = MutableStateFlow(false)
    val isAskingAdvisor: StateFlow<Boolean> = _isAskingAdvisor.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private val _driveRestoreOffer = MutableStateFlow<CloudBackupFile?>(null)
    val driveRestoreOffer: StateFlow<CloudBackupFile?> = _driveRestoreOffer.asStateFlow()

    private val _profilePhotoEpoch = MutableStateFlow(0L)
    val profilePhotoEpoch: StateFlow<Long> = _profilePhotoEpoch.asStateFlow()

    private val _liquidityForecast = MutableStateFlow<LiquidityForecastOutput?>(null)
    val liquidityForecast: StateFlow<LiquidityForecastOutput?> = _liquidityForecast.asStateFlow()

    private val _isForecasting = MutableStateFlow(false)
    val isForecasting: StateFlow<Boolean> = _isForecasting.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _backupError = MutableStateFlow<String?>(null)
    val backupError: StateFlow<String?> = _backupError.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _cloudBackups = MutableStateFlow<List<CloudBackupFile>>(emptyList())
    val cloudBackups: StateFlow<List<CloudBackupFile>> = _cloudBackups.asStateFlow()

    private val _isScanningSms = MutableStateFlow(false)
    val isScanningSms: StateFlow<Boolean> = _isScanningSms.asStateFlow()

    private val _smsScanProgress = MutableStateFlow<String?>(null)
    val smsScanProgress: StateFlow<String?> = _smsScanProgress.asStateFlow()

    private val _inboxNotice = MutableStateFlow<String?>(null)
    val inboxNotice: StateFlow<String?> = _inboxNotice.asStateFlow()

    private val _canPostNotifications = MutableStateFlow(true)
    val canPostNotifications: StateFlow<Boolean> = _canPostNotifications.asStateFlow()

    private val _smsPermissionGranted = MutableStateFlow(false)
    val smsPermissionGranted: StateFlow<Boolean> = _smsPermissionGranted.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private val _smsPermissionPrompt = MutableStateFlow(false)
    val smsPermissionPrompt: StateFlow<Boolean> = _smsPermissionPrompt.asStateFlow()

    private val _latestToast = MutableStateFlow<AppToast?>(null)
    val latestToast: StateFlow<AppToast?> = _latestToast.asStateFlow()
    private val _signedOut = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedOut: kotlinx.coroutines.flow.SharedFlow<Unit> = _signedOut
    private val _vaultUnlocked = MutableStateFlow(true)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()
    private var quietToasts = false
    private var pendingInboxUndo: InboxUndoAction? = null
    private var snapshotEpoch = 0
    private var toastSeq = 0L

    fun lockVault() {
        if (_state.value.vaultLockEnabled) _vaultUnlocked.value = false
    }

    fun unlockWithDevice() {
        _vaultUnlocked.value = true
        requestStartupPermissions()
    }

    fun unlockVault(pin: String): Boolean {
        if (!repository.verifyVaultPin(pin)) return false
        _vaultUnlocked.value = true
        requestStartupPermissions()
        return true
    }

    fun setVaultPin(pin: String, confirm: String): Boolean {
        if (pin != confirm || !isValidVaultPin(pin)) return false
        repository.setVaultPin(pin)
        patchState { it.copy(vaultLockEnabled = true) }
        _vaultUnlocked.value = true
        toast("App lock on")
        return true
    }

    fun disableVaultLock(currentPin: String): Boolean {
        if (!repository.verifyVaultPin(currentPin)) return false
        repository.clearVaultPin()
        patchState { it.copy(vaultLockEnabled = false) }
        _vaultUnlocked.value = true
        toast("App lock off")
        return true
    }

    fun changeVaultPin(currentPin: String, nextPin: String, confirm: String): Boolean {
        if (!repository.verifyVaultPin(currentPin)) return false
        if (nextPin != confirm || !isValidVaultPin(nextPin)) return false
        repository.setVaultPin(nextPin)
        _vaultUnlocked.value = true
        toast("PIN updated")
        return true
    }

    private fun toast(message: String) {
        pendingInboxUndo = null
        emitToast(AppToast(message))
    }

    private fun toastUndo(message: String, undo: InboxUndoAction) {
        pendingInboxUndo = undo
        emitToast(AppToast(message, actionLabel = "Undo"))
    }

    private fun emitToast(toast: AppToast) {
        if (quietToasts) return
        _latestToast.value = toast.copy(id = ++toastSeq)
    }

    fun performToastAction() {
        val undo = pendingInboxUndo ?: return
        pendingInboxUndo = null
        undoInboxAction(undo)
    }

    fun consumeToast(shown: AppToast) {
        if (_latestToast.value?.id != shown.id) return
        pendingInboxUndo = null
        _latestToast.value = null
    }

    private val actionGate = ActionGate()

    private inline fun <T> writeOnce(key: String, fallback: T, cooldownMs: Long = 800L, block: () -> T): T {
        if (!actionGate.tryStart(key, cooldownMs)) return fallback
        try {
            return block()
        } finally {
            actionGate.finish(key)
        }
    }

    private inline fun writeOnce(key: String, cooldownMs: Long = 800L, block: () -> Unit) {
        writeOnce(key, Unit, cooldownMs, block)
    }

    private fun launchOnce(key: String, block: suspend () -> Unit) {
        if (!actionGate.tryStart(key)) return
        scope.launch {
            try {
                block()
            } finally {
                actionGate.finish(key)
            }
        }
    }

    private inline fun <T> quietly(block: () -> T): T {
        val previous = quietToasts
        quietToasts = true
        try {
            return block()
        } finally {
            quietToasts = previous
        }
    }

    private fun announceListCrud(noun: String, beforeIds: List<String>, afterIds: List<String>, changed: Boolean) {
        val added = afterIds.toSet() - beforeIds.toSet()
        val removed = beforeIds.toSet() - afterIds.toSet()
        when {
            added.size == 1 && removed.isEmpty() -> toast("$noun added")
            removed.size == 1 && added.isEmpty() -> toast("$noun deleted")
            added.isEmpty() && removed.isEmpty() && changed -> toast("$noun updated")
            added.isNotEmpty() || removed.isNotEmpty() -> toast("${noun}s updated")
        }
    }

    private fun inboxFeedback(message: String) {
        _inboxNotice.value = null
        toast(message)
    }

    private fun expenseSavedToast(previous: Expense?, type: String, amount: Double, sourceId: String?): String {
        if (previous != null) return "Transaction updated"
        if (type == "income") return "Income added"
        if (type == Constants.TYPE_TRANSFER) return "Transfer added"
        val source = sourceId?.let { sid -> _state.value.paymentSources.find { it.id == sid } }
        return if (source?.kind == Constants.SOURCE_CREDIT_CARD) {
            "${formatINR(amount)} added to ${source.name} outstanding"
        } else {
            "Expense added"
        }
    }

    private fun inboxPostedFeedback(posted: Expense?): String {
        if (posted == null) return "Couldn’t add that inbox item."
        val source = posted.sourceId?.let { sid -> _state.value.paymentSources.find { it.id == sid } }
        return if (posted.isSpend() && source?.kind == Constants.SOURCE_CREDIT_CARD) {
            "${formatINR(posted.amount)} added to ${source.name} outstanding."
        } else {
            "Added to history."
        }
    }

    fun initialize() {
        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    runCatching { repository.initialize() }
                    refreshNow()
                    if (_state.value.vaultLockEnabled) _vaultUnlocked.value = false
                }
            } catch (_: Throwable) {
                toast("Could not open your vault")
            } finally {
                _isLoading.value = false
            }

            // Non-critical startup tasks: run in background after UI is visible
            scope.launch(Dispatchers.Default) {
                delay(600)
                refreshInboxCapturePermissions()
                reminderScheduler.scheduleDailyReminder(_state.value.inboxReminder)
                if (!_state.value.vaultLockEnabled) requestStartupPermissions()
            }
        }
    }

    private var startupPermissionsAsked = false

    private fun requestStartupPermissions() {
        if (startupPermissionsAsked) return
        startupPermissionsAsked = true
        scope.launch {
            delay(500)
            if (inboxCapture.smsCaptureSupported() && !inboxCapture.smsPermissionGranted()) {
                inboxCapture.requestSmsPermission()
            }
            if (!reminderScheduler.notificationPermissionGranted()) {
                reminderScheduler.requestNotificationPermission()
            }
            refreshInboxCapturePermissions()
        }
    }

    fun setUseDeviceLock(enabled: Boolean) {
        repository.setUseDeviceLock(enabled)
        refresh()
    }

    fun refresh() {
        val epoch = ++snapshotEpoch
        scope.launch {
            val snapshot = withContext(Dispatchers.Default) { repository.loadSnapshot() }
            if (epoch != snapshotEpoch) return@launch
            applySnapshot(snapshot)
        }
    }

    private fun refreshNow() {
        snapshotEpoch++
        applySnapshot(repository.loadSnapshot())
    }

    private fun applySnapshot(snapshot: FinanceSnapshot) {
        _state.value = snapshot
        rememberTheme(snapshot.userProfile?.theme)
    }

    private fun patchState(transform: (FinanceSnapshot) -> FinanceSnapshot) {
        val next = transform(_state.value)
        _state.value = next
        rememberTheme(next.userProfile?.theme)
    }

    private fun rememberTheme(theme: String?) {
        val pref = normalizeTheme(theme)
        if (_themePreference.value != pref) _themePreference.value = pref
    }

    fun addExpense(
        amount: Double,
        categoryId: String,
        note: String?,
        type: String,
        sourceId: String? = null,
        timestamp: String? = null,
        destinationId: String? = null,
        id: String? = null,
        dueKind: String? = null,
        dueEntityId: String? = null,
    ): Boolean = writeOnce("addExpense", false) {
        runCatching {
            val normalizedAmount = roundCurrency(amount)
            val previous = id?.let { existingId -> _state.value.expenses.find { it.id == existingId } }
            val expense = Expense(
                id = id ?: generateId(),
                amount = normalizedAmount,
                categoryId = categoryId,
                timestamp = timestamp ?: Clock.System.now().toString(),
                note = note,
                type = type,
                sourceId = sourceId,
                destinationId = destinationId,
                dueKind = dueKind ?: previous?.dueKind,
                dueEntityId = dueEntityId ?: previous?.dueEntityId,
            )
            val candidateIssues = checkTransactionCandidate(expense, _state.value.expenses, _state.value.paymentSources)
            val duplicateWarning = candidateIssues.firstOrNull { it.startsWith("Potential duplicate") }

            repository.addExpense(expense)
            if (!sourceId.isNullOrBlank()) repository.setLastPaymentSourceId(sourceId)
            syncTransferDestinations(previous, expense)
            patchState { state ->
                val expenses = if (previous != null) {
                    state.expenses.map { if (it.id == expense.id) expense else it }
                } else {
                    listOf(expense) + state.expenses
                }
                state.copy(
                    expenses = expenses,
                    lastPaymentSourceId = sourceId?.takeIf { it.isNotBlank() } ?: state.lastPaymentSourceId,
                )
            }
            if (previous == null && duplicateWarning != null) {
                toast(duplicateWarning)
            } else {
                toast(expenseSavedToast(previous, type, normalizedAmount, sourceId))
            }
            true
        }.getOrElse {
            toast("Could not save transaction")
            false
        }
    }

    fun updateExpense(expense: Expense) = writeOnce("updateExpense") {
        runCatching {
            val normalized = expense.copy(amount = roundCurrency(expense.amount))
            val previous = _state.value.expenses.find { it.id == normalized.id }
            repository.updateExpense(normalized)
            if (!normalized.sourceId.isNullOrBlank()) repository.setLastPaymentSourceId(normalized.sourceId)
            syncTransferDestinations(previous, normalized)
            patchState { state ->
                state.copy(
                    expenses = state.expenses.map { if (it.id == normalized.id) normalized else it },
                    lastPaymentSourceId = normalized.sourceId?.takeIf { it.isNotBlank() } ?: state.lastPaymentSourceId,
                )
            }
            toast("Transaction updated")
        }.onFailure { toast("Could not update transaction") }
    }

    fun deleteExpense(id: String) = writeOnce("deleteExpense") {
        runCatching {
            val previous = _state.value.expenses.find { it.id == id }
            repository.deleteExpense(id)
            syncTransferDestinations(previous, null)
            patchState { it.copy(expenses = it.expenses.filter { expense -> expense.id != id }) }
            toast("Transaction deleted")
        }.onFailure { toast("Could not delete transaction") }
    }

    fun splitExpense(
        id: String,
        firstCategoryId: String,
        firstAmount: Double,
        secondCategoryId: String,
    ): Boolean {
        val original = _state.value.expenses.find { it.id == id } ?: return false
        val parts = planExpenseSplit(
            original = original,
            firstCategoryId = firstCategoryId,
            firstAmount = roundCurrency(firstAmount),
            secondCategoryId = secondCategoryId,
            firstId = generateId(),
            secondId = generateId(),
        ) ?: return false
        val firstNorm = parts.first.copy(amount = roundCurrency(parts.first.amount))
        val secondNorm = parts.second.copy(amount = roundCurrency(parts.second.amount))
        return runCatching {
            repository.deleteExpense(original.id)
            repository.addExpense(firstNorm)
            repository.addExpense(secondNorm)
            patchState { state ->
                val without = state.expenses.filter { it.id != original.id }
                state.copy(expenses = listOf(firstNorm, secondNorm) + without)
            }
            toast("Transaction split")
            true
        }.getOrElse {
            toast("Could not split transaction")
            false
        }
    }

    fun processDueItems() {
        scope.launch {
            withContext(Dispatchers.Default) {
                processDueItemsInternal()
                refreshNow()
            }
        }
    }

    fun contributeToGoal(goalId: String, amount: Double, sourceId: String?) {
        val goal = _state.value.savingsGoals.find { it.id == goalId } ?: return
        quietly {
            addExpense(
                amount = amount,
                categoryId = transferCategoryId(),
                note = "Saved toward ${goal.name}",
                type = Constants.TYPE_TRANSFER,
                sourceId = sourceId,
                destinationId = Constants.goalDestination(goalId),
            )
        }
        toast("${formatINR(amount)} added to ${goal.name}")
    }

    fun contributeToEmergency(amount: Double, sourceId: String?) {
        quietly {
            addExpense(
                amount = amount,
                categoryId = transferCategoryId(),
                note = "Emergency fund",
                type = Constants.TYPE_TRANSFER,
                sourceId = sourceId,
                destinationId = Constants.DEST_EMERGENCY,
            )
        }
        toast("${formatINR(amount)} added to emergency fund")
    }

    fun markDuePaid(kind: String, entityId: String, sourceId: String? = null): Boolean = writeOnce("markDue:$kind:$entityId", false) {
        val snapshot = _state.value
        val candidate = openDueCandidates(includeFuture = true)
            .firstOrNull { it.kind == kind && it.entityId == entityId } ?: return false
        val paidFrom = sourceId?.takeIf { it.isNotBlank() }
            ?: resolveDuePaymentSourceId(candidate.itemSourceId, snapshot.lastPaymentSourceId)
            ?: return false
        postDueCandidate(candidate, paidFrom)
        refreshNow()
        toast("Marked as paid")
        return true
    }

    fun skipDue(kind: String, entityId: String): Boolean = writeOnce("skipDue:$kind:$entityId", false) {
        val candidate = openDueCandidates(includeFuture = true)
            .firstOrNull { it.kind == kind && it.entityId == entityId } ?: return false
        markDueProcessed(candidate, monthKey(candidate.dueOn), postedSourceId = null, posted = false)
        refreshNow()
        toast("Skipped this due")
        return true
    }

    fun undoDue(kind: String, entityId: String): Boolean {
        val snapshot = _state.value
        val posted = snapshot.expenses
            .filter { it.dueKind == kind && it.dueEntityId == entityId }
            .maxByOrNull { it.timestamp }
        if (posted != null) {
            repository.deleteExpense(posted.id)
            syncTransferDestinations(posted, null)
        }
        when (kind) {
            DUE_KIND_SIP -> {
                repository.saveInvestments(
                    snapshot.investments.map { inv ->
                        if (inv.id != entityId) inv
                        else inv.copy(
                            investedAmount = if (posted != null) {
                                ((inv.investedAmount ?: 0.0) - inv.amount).coerceAtLeast(0.0)
                            } else inv.investedAmount,
                            lastProcessedMonth = null,
                        )
                    },
                )
            }
            DUE_KIND_INSURANCE -> {
                repository.saveInsurances(
                    snapshot.insurances.map { ins ->
                        if (ins.id != entityId) ins else ins.copy(lastProcessedMonth = null)
                    },
                )
            }
            DUE_KIND_EMI -> {
                repository.saveLoans(
                    snapshot.loans.map { loan ->
                        if (loan.id != entityId) loan
                        else if (posted != null) reverseEmiPayment(loan) else loan.copy(lastProcessedMonth = null)
                    },
                )
            }
            DUE_KIND_BILL, DUE_KIND_SALARY -> {
                repository.saveRecurringBills(
                    snapshot.recurringBills.map { bill ->
                        if (bill.id != entityId) bill else bill.copy(lastProcessedMonth = null)
                    },
                )
            }
            DUE_KIND_CARD -> {
                snapshot.paymentSources.find { it.id == entityId }?.let { card ->
                    repository.savePaymentSource(card.copy(lastProcessedMonth = null))
                }
            }
            else -> return false
        }
        refresh()
        toast("Due undone")
        return true
    }

    fun addBorrowLend(
        person: String,
        amount: Double,
        type: String,
        note: String?,
        personId: String? = null,
        phone: String? = null,
        timestamp: String? = null,
        id: String? = null,
        settled: Boolean? = null,
        sourceId: String? = null,
    ) {
        val resolved = resolvePerson(person, personId, phone)
        val previous = id?.let { existingId -> _state.value.borrowLend.find { it.id == existingId } }
        val normalizedAmount = roundCurrency(amount)
        val item = BorrowLend(
            id = previous?.id ?: id ?: generateId(),
            person = resolved.name,
            amount = normalizedAmount,
            type = type,
            settled = settled ?: previous?.settled ?: false,
            timestamp = timestamp ?: Clock.System.now().toString(),
            note = note,
            personId = resolved.id,
        )
        repository.addBorrowLend(item)
        patchLending(item, previous != null, resolved)

        val paidFrom = sourceId?.takeIf { it.isNotBlank() }
        val linkedExpenseId = "${item.id}_mov"
        val existingLinkedExpense = _state.value.expenses.find { it.id == linkedExpenseId || it.dueEntityId == item.id }
        if (paidFrom != null) {
            val expenseType = if (type == "lend") "expense" else "income"
            val categoryId = if (type == "lend") {
                _state.value.categories.find { it.id == "cat_loans" }?.id ?: "cat_loans"
            } else {
                _state.value.categories.find { it.id == "cat_other_inc" }?.id ?: "cat_other_inc"
            }
            val expenseNote = if (type == "lend") {
                "Lent to ${item.person}${note?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
            } else {
                "Borrowed from ${item.person}${note?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
            }
            quietly {
                addExpense(
                    amount = normalizedAmount,
                    categoryId = categoryId,
                    note = expenseNote,
                    type = expenseType,
                    sourceId = paidFrom,
                    timestamp = item.timestamp,
                    id = existingLinkedExpense?.id ?: linkedExpenseId,
                    dueKind = if (type == "lend") "lending" else "borrow",
                    dueEntityId = item.id,
                )
            }
        } else if (existingLinkedExpense != null) {
            quietly { deleteExpense(existingLinkedExpense.id) }
        }

        toast(
            when {
                previous != null -> "Lending updated"
                type == "borrow" -> if (paidFrom != null) "Borrow added & credited to account" else "Borrow added"
                else -> if (paidFrom != null) "Lend added & debited from account" else "Lend added"
            },
        )
    }

    fun updateBorrowLend(
        id: String,
        person: String,
        amount: Double,
        type: String,
        note: String?,
        personId: String? = null,
        phone: String? = null,
        timestamp: String? = null,
        settled: Boolean? = null,
        sourceId: String? = null,
    ) {
        if (_state.value.borrowLend.none { it.id == id }) return
        addBorrowLend(
            person = person,
            amount = amount,
            type = type,
            note = note,
            personId = personId,
            phone = phone,
            timestamp = timestamp,
            id = id,
            settled = settled,
            sourceId = sourceId,
        )
    }

    fun updateBorrowLend(
        id: String,
        amount: Double,
        type: String,
        note: String?,
        timestamp: String,
        settled: Boolean,
    ) {
        val item = _state.value.borrowLend.find { it.id == id } ?: return
        repository.addBorrowLend(
            item.copy(
                amount = amount,
                type = type,
                note = note,
                timestamp = timestamp,
                settled = settled,
            ),
        )
        refresh()
        toast("Lending updated")
    }

    fun ensurePerson(name: String, phone: String? = null): Person {
        val person = resolvePerson(name, null, phone)
        refresh()
        return person
    }

    fun deleteBorrowLend(id: String) {
        val linkedExpense = _state.value.expenses.find { it.id == "${id}_mov" || it.dueEntityId == id }
        if (linkedExpense != null) {
            quietly { deleteExpense(linkedExpense.id) }
        }
        repository.deleteBorrowLend(id)
        patchState { it.copy(borrowLend = it.borrowLend.filter { item -> item.id != id }) }
        toast("Lending deleted")
    }

    fun settleBorrowLend(id: String) {
        val item = _state.value.borrowLend.find { it.id == id } ?: return
        val next = item.copy(settled = true)
        repository.addBorrowLend(next)
        patchState { it.copy(borrowLend = it.borrowLend.map { row -> if (row.id == id) next else row }) }
        toast("Marked as settled")
    }

    fun repayBorrowLend(id: String, sourceId: String? = null): Boolean = writeOnce("repayBorrowLend:$id", false) {
        val item = _state.value.borrowLend.find { it.id == id } ?: return false
        if (item.settled) return false
        val opposite = if (item.type == "borrow") "lend" else "borrow"
        val paidFrom = sourceId?.takeIf { it.isNotBlank() }
        quietly {
            // Mark the original log as settled
            val settledOriginal = item.copy(settled = true)
            repository.addBorrowLend(settledOriginal)
            patchState { s -> s.copy(borrowLend = s.borrowLend.map { if (it.id == id) settledOriginal else it }) }

            // Record repayment with settled=true and reflect in selected account if specified
            addBorrowLend(
                person = item.person,
                amount = item.amount,
                type = opposite,
                note = "Repayment: ${item.note ?: "settled"}",
                personId = item.personId,
                settled = true,
                sourceId = paidFrom,
            )
        }
        toast(if (paidFrom != null) "Repayment recorded & account updated" else "Repayment recorded")
        return true
    }

    fun repayPerson(person: String, personId: String?, amount: Double, note: String? = "Repay", sourceId: String? = null): Boolean = writeOnce("repayPerson:$personId:$person", false) {
        if (!amount.isFinite() || amount <= 0.0) return false
        val net = getPersonLendingNets(_state.value.borrowLend, _state.value.people)
            .firstOrNull { row ->
                (personId != null && row.personId == personId) ||
                    row.person.equals(person, ignoreCase = true)
            }?.net ?: 0.0
        if (net == 0.0) return false
        val type = if (net > 0) "borrow" else "lend"
        val paidFrom = sourceId?.takeIf { it.isNotBlank() }
        quietly {
            addBorrowLend(
                person = person,
                amount = amount,
                type = type,
                note = note,
                personId = personId,
                sourceId = paidFrom,
            )
        }
        toast(if (paidFrom != null) "Repayment recorded & account updated" else "Repayment recorded")
        return true
    }

    fun settlePerson(person: String, personId: String? = null) = writeOnce("settlePerson:$personId:$person") {
        _state.value.borrowLend
            .filter { !it.settled }
            .filter { item ->
                (personId != null && item.personId == personId) ||
                    item.person.equals(person, ignoreCase = true)
            }
            .forEach { repository.addBorrowLend(it.copy(settled = true)) }
        patchState { state ->
            state.copy(
                borrowLend = state.borrowLend.map { item ->
                    val match = !item.settled && (
                        (personId != null && item.personId == personId) ||
                            item.person.equals(person, ignoreCase = true)
                    )
                    if (match) item.copy(settled = true) else item
                },
            )
        }
        toast("Settled with $person")
    }

    fun savePerson(person: Person) {
        val existed = _state.value.people.any { it.id == person.id }
        repository.savePerson(person)
        patchState { state ->
            val people = if (existed) {
                state.people.map { if (it.id == person.id) person else it }
            } else {
                state.people + person
            }
            state.copy(people = people)
        }
        toast(if (existed) "Contact updated" else "Contact added")
    }

    fun saveBudgets(budgets: List<Budget>) {
        val before = _state.value.budgets
        repository.saveBudgets(budgets)
        patchState { it.copy(budgets = budgets) }
        if (before != budgets) toast("Budgets saved")
    }

    fun saveInvestments(investments: List<Investment>) {
        val before = _state.value.investments
        repository.saveInvestments(investments)
        patchState { it.copy(investments = investments) }
        announceListCrud("Investment", before.map { it.id }, investments.map { it.id }, before != investments)
    }

    fun saveInsurances(insurances: List<Insurance>) {
        val before = _state.value.insurances
        repository.saveInsurances(insurances)
        patchState { it.copy(insurances = insurances) }
        announceListCrud("Insurance", before.map { it.id }, insurances.map { it.id }, before != insurances)
    }

    fun saveRecurringBills(bills: List<RecurringBill>) {
        val before = _state.value.recurringBills
        repository.saveRecurringBills(bills)
        patchState { it.copy(recurringBills = bills) }
        val beforeIds = before.map { it.id }.toSet()
        val afterIds = bills.map { it.id }.toSet()
        val added = afterIds - beforeIds
        val removed = beforeIds - afterIds
        when {
            added.size == 1 && removed.isEmpty() -> {
                val item = bills.first { it.id == added.first() }
                toast(if (item.isIncome()) "Salary added" else "Bill added")
            }
            removed.size == 1 && added.isEmpty() -> {
                val item = before.first { it.id == removed.first() }
                toast(if (item.isIncome()) "Salary deleted" else "Bill deleted")
            }
            added.isEmpty() && removed.isEmpty() && before != bills -> {
                val changed = bills.filter { next -> before.find { it.id == next.id } != next }
                toast(
                    if (changed.size == 1 && changed.first().isIncome()) "Salary updated"
                    else "Bill updated",
                )
            }
            added.isNotEmpty() || removed.isNotEmpty() -> toast("Bills updated")
        }
    }

    fun skipSetup() = writeOnce("skipSetup") {
        repository.setSetupCompleted(true)
        patchState { it.copy(setupCompleted = true) }
        toast("Setup skipped")
    }

    fun completeAccountSetup(
        name: String,
        kind: String = Constants.SOURCE_BANK,
        last4: String? = null,
        balanceOrOutstanding: Double = 0.0,
    ) = writeOnce("completeAccountSetup") {
        val trimmed = name.trim()
        quietly {
            if (trimmed.isNotEmpty()) {
                addPaymentSource(
                    name = trimmed,
                    kind = kind,
                    last4 = last4?.trim()?.takeIf { it.isNotEmpty() },
                    icon = defaultSourceIcon(kind),
                    color = defaultSourceColor(kind),
                    openingBalance = if (balanceOrOutstanding.isFinite() && balanceOrOutstanding > 0) balanceOrOutstanding else 0.0,
                )
            }
            repository.setSetupCompleted(true)
            patchState { it.copy(setupCompleted = true) }
            refresh()
        }
        toast(if (trimmed.isNotEmpty()) "Account configured" else "Welcome to KashVault")
    }

    fun completeSetup(
        bankName: String,
        bankBalance: Double,
        salaryAmount: Double,
        salaryDay: Int,
        rentAmount: Double,
        rentDay: Int,
    ) = writeOnce("completeSetup") {
        val trimmedBank = bankName.trim()
        val bills = _state.value.recurringBills.toMutableList()
        quietly {
            val sourceId = if (trimmedBank.isNotEmpty()) {
                addPaymentSource(
                    name = trimmedBank,
                    kind = Constants.SOURCE_BANK,
                    last4 = null,
                    icon = defaultSourceIcon(Constants.SOURCE_BANK),
                    color = defaultSourceColor(Constants.SOURCE_BANK),
                    openingBalance = if (bankBalance.isFinite() && bankBalance > 0) bankBalance else 0.0,
                )
            } else {
                _state.value.lastPaymentSourceId ?: _state.value.paymentSources.firstOrNull { it.kind != Constants.SOURCE_CREDIT_CARD }?.id
            }
            if (salaryAmount.isFinite() && salaryAmount > 0) {
                bills += RecurringBill(
                    id = generateId(),
                    name = "Salary",
                    amount = salaryAmount,
                    categoryId = "cat_salary",
                    frequency = "Monthly",
                    dueDate = dueDateOnDay(salaryDay),
                    sourceId = sourceId,
                    type = "income",
                )
            }
            if (rentAmount.isFinite() && rentAmount > 0) {
                bills += RecurringBill(
                    id = generateId(),
                    name = "Home Rent",
                    amount = rentAmount,
                    categoryId = "cat_rent",
                    frequency = "Monthly",
                    dueDate = dueDateOnDay(rentDay),
                    sourceId = sourceId,
                    type = "expense",
                )
            }
            if (bills != _state.value.recurringBills) repository.saveRecurringBills(bills)
            repository.setSetupCompleted(true)
            refresh()
        }
        toast("Setup saved")
    }

    fun saveLoans(loans: List<Loan>) {
        val before = _state.value.loans
        repository.saveLoans(loans)
        patchState { it.copy(loans = loans) }
        announceListCrud("Loan", before.map { it.id }, loans.map { it.id }, before != loans)
    }

    fun prepayLoan(loanId: String, amount: Double, sourceId: String): Boolean = writeOnce("prepayLoan:$loanId", false) {
        val snapshot = _state.value
        val loan = snapshot.loans.find { it.id == loanId } ?: return false
        val outstanding = getLoanOutstandingTotal(loan)
        val pay = roundCurrency(minOf(amount, outstanding).coerceAtLeast(0.0))
        if (pay <= 0.0) {
            toast("Nothing outstanding on ${loan.name}")
            return false
        }
        val bank = snapshot.paymentSources.find { it.id == sourceId && it.kind != Constants.SOURCE_CREDIT_CARD }
        if (bank == null) {
            toast("Pick a bank to prepay from")
            return false
        }
        val closes = pay >= outstanding
        val categoryId = snapshot.categories.find { it.id == "cat_loans" }?.id
            ?: snapshot.categories.firstOrNull { it.type != "income" }?.id
            ?: INITIAL_CATEGORIES.first().id
        val saved = quietly {
            addExpense(
                amount = pay,
                categoryId = categoryId,
                note = if (closes) "Loan close · ${loan.name}" else "Prepay · ${loan.name}",
                type = "expense",
                sourceId = sourceId,
                dueKind = DUE_KIND_LOAN_PREPAY,
                dueEntityId = loan.id,
            )
        }
        if (!saved) return false
        val next = applyLoanPrepayment(loan.copy(sourceId = loan.sourceId ?: sourceId), pay)
        repository.saveLoans(snapshot.loans.map { if (it.id == loan.id) next else it })
        patchState { it.copy(loans = it.loans.map { row -> if (row.id == loan.id) next else row }) }
        val left = getLoanOutstandingTotal(next)
        toast(
            if (closes) {
                "Closed ${loan.name}"
            } else {
                "${formatINR(pay)} prepaid on ${loan.name}. Outstanding ${formatINR(left)}"
            },
        )
        return true
    }

    fun saveSavingsGoals(goals: List<SavingsGoal>) {
        val before = _state.value.savingsGoals
        repository.saveSavingsGoals(goals)
        patchState { it.copy(savingsGoals = goals) }
        announceListCrud("Goal", before.map { it.id }, goals.map { it.id }, before != goals)
    }

    fun saveEmergencyFund(fund: EmergencyFund) {
        val before = _state.value.emergencyFund
        repository.saveEmergencyFund(fund)
        patchState { it.copy(emergencyFund = fund) }
        if (before != fund) toast("Emergency fund updated")
    }

    fun saveCategory(category: Category) {
        val existed = _state.value.categories.any { it.id == category.id }
        repository.saveCategory(category)
        patchState { state ->
            val categories = if (existed) {
                state.categories.map { if (it.id == category.id) category else it }
            } else {
                state.categories + category
            }
            state.copy(categories = categories)
        }
        toast(if (existed) "Category updated" else "Category added")
    }

    fun saveSmsScanKeywords(text: String) {
        repository.saveSmsScanKeywords(keywordsFromText(text))
        patchState { it.copy(smsScanKeywords = keywordsFromText(text)) }
        toast("Scan keywords saved")
    }

    fun setInboxReminderEnabled(enabled: Boolean) {
        scope.launch {
            if (enabled) {
                val granted = reminderScheduler.notificationPermissionGranted() ||
                    reminderScheduler.requestNotificationPermission()
                refreshInboxReminderPermission()
                if (!granted) {
                    toast("Turn on notifications to get the nightly inbox reminder")
                }
            }
            val next = _state.value.inboxReminder.copy(enabled = enabled)
            repository.saveInboxReminderSettings(next)
            patchState { it.copy(inboxReminder = next) }
            reminderScheduler.scheduleDailyReminder(next)
            toast(if (enabled) "Nightly inbox reminder on" else "Nightly inbox reminder off")
        }
    }

    fun setInboxReminderTime(hour: Int, minute: Int) {
        val next = _state.value.inboxReminder.copy(
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
        )
        repository.saveInboxReminderSettings(next)
        patchState { it.copy(inboxReminder = next) }
        reminderScheduler.scheduleDailyReminder(next)
        toast("Reminder time set to ${formatReminderClock(next.hour, next.minute)}")
    }

    fun refreshInboxReminderPermission() {
        _canPostNotifications.value = reminderScheduler.notificationPermissionGranted()
    }

    fun refreshInboxCapturePermissions() {
        _smsPermissionGranted.value = inboxCapture.smsPermissionGranted()
        _notificationAccessGranted.value = inboxCapture.notificationAccessEnabled()
        refreshInboxReminderPermission()
    }

    fun requestInboxReminderPermission() {
        scope.launch {
            val granted = reminderScheduler.notificationPermissionGranted() ||
                reminderScheduler.requestNotificationPermission()
            refreshInboxReminderPermission()
            if (!granted) reminderScheduler.openNotificationSettings()
        }
    }

    fun openInboxReminderNotificationSettings() = reminderScheduler.openNotificationSettings()

    fun addCategory(name: String, icon: String, color: String, type: String, keywords: List<String>) {
        saveCategory(
            Category(
                id = generateId(),
                name = name,
                icon = icon,
                color = color,
                type = type,
                keywords = keywords,
            ),
        )
    }

    fun deleteCategory(id: String) {
        repository.deleteCategory(id)
        patchState { it.copy(categories = it.categories.filter { category -> category.id != id }) }
        toast("Category deleted")
    }

    fun savePaymentSource(source: PaymentSource) {
        val existed = _state.value.paymentSources.any { it.id == source.id }
        repository.savePaymentSource(source)
        patchState { state ->
            val sources = if (existed) {
                state.paymentSources.map { if (it.id == source.id) source else it }
            } else {
                state.paymentSources + source
            }
            state.copy(paymentSources = sources)
        }
        toast(if (existed) "Account updated" else "Account added")
    }

    fun addPaymentSource(
        name: String,
        kind: String,
        last4: String?,
        icon: String,
        color: String,
        keywords: List<String> = emptyList(),
        openingBalance: Double = 0.0,
        statementDueDate: String? = null,
        payFromSourceId: String? = null,
    ): String {
        val digits = normalizeLast4(last4)
        val tags = keywords.ifEmpty { defaultSourceTags(name.trim(), digits) }
        val id = generateId()
        savePaymentSource(
            PaymentSource(
                id = id,
                name = name.trim(),
                kind = kind,
                last4 = digits,
                icon = icon.ifBlank { defaultSourceIcon(kind) },
                color = color.ifBlank { defaultSourceColor(kind) },
                keywords = tags,
                openingBalance = openingBalance,
                statementDueDate = statementDueDate,
                payFromSourceId = payFromSourceId,
            ),
        )
        return id
    }

    fun deletePaymentSource(id: String) {
        repository.deletePaymentSource(id)
        if (_state.value.lastPaymentSourceId == id) {
            repository.setLastPaymentSourceId(null)
        }
        patchState { state ->
            state.copy(
                paymentSources = state.paymentSources.filter { it.id != id },
                lastPaymentSourceId = if (state.lastPaymentSourceId == id) null else state.lastPaymentSourceId,
            )
        }
        toast("Account deleted")
    }

    fun payCardBill(
        cardId: String,
        fromSourceId: String,
        amount: Double? = null,
        timestamp: String? = null,
        id: String? = null,
    ): Boolean {
        val snapshot = _state.value
        val card = snapshot.paymentSources.find { it.id == cardId && it.kind == Constants.SOURCE_CREDIT_CARD }
        if (card == null) return false
        val bank = snapshot.paymentSources.find { it.id == fromSourceId && it.kind != Constants.SOURCE_CREDIT_CARD }
        if (bank == null) {
            toast("Pick a bank to pay from")
            return false
        }
        val owed = sourceDisplayedBalance(card, snapshot.expenses)
        val pay = amount?.takeIf { it > 0.0 } ?: owed
        if (pay <= 0.0 || owed <= 0.0) {
            toast("Nothing outstanding on ${card.name}")
            return false
        }
        val saved = quietly {
            addExpense(
                amount = pay,
                categoryId = transferCategoryId(),
                note = "Card bill · ${card.name}",
                type = Constants.TYPE_TRANSFER,
                sourceId = fromSourceId,
                destinationId = cardId,
                timestamp = timestamp,
                id = id,
            )
        }
        if (!saved) return false
        if (card.payFromSourceId != fromSourceId) {
            repository.savePaymentSource(card.copy(payFromSourceId = fromSourceId))
            patchState { state ->
                state.copy(
                    paymentSources = state.paymentSources.map {
                        if (it.id == cardId) it.copy(payFromSourceId = fromSourceId) else it
                    },
                )
            }
        }
        toast("Paid ${formatINR(pay)} toward ${card.name}")
        return true
    }

    fun saveProfile(profile: UserProfile) = writeOnce("saveProfile") {
        repository.saveProfile(profile)
        patchState { it.copy(userProfile = profile) }
        toast("Profile updated")
    }

    fun setGeminiModel(model: String) {
        val normalized = normalizeGeminiModel(model)
        if (_state.value.geminiModel == normalized) return
        repository.saveGeminiModel(normalized)
        patchState { it.copy(geminiModel = normalized) }
    }

    fun setTheme(theme: String) {
        val current = _state.value.userProfile
        val normalized = normalizeTheme(theme)
        if (normalizeTheme(current?.theme) == normalized) return
        val profile = UserProfile(
            id = current?.id ?: Constants.SINGLETON_ID,
            name = current?.name.orEmpty(),
            email = current?.email.orEmpty(),
            mobile = current?.mobile.orEmpty(),
            geminiApiKey = current?.geminiApiKey,
            theme = normalized,
            imagePath = current?.imagePath,
        )
        patchState { it.copy(userProfile = profile) }
        repository.saveProfile(profile)
        toast("${themeLabel(normalized)} theme")
    }

    fun pickProfileImage() {
        scope.launch {
            val bytes = runCatching { imagePicker.pickImage() }.getOrNull() ?: return@launch
            val path = repository.profileImageFilePath()
            writeFileBytes(path, bytes)
            val existing = _state.value.userProfile
            repository.saveProfile(
                UserProfile(
                    id = existing?.id ?: Constants.SINGLETON_ID,
                    name = existing?.name.orEmpty(),
                    email = existing?.email.orEmpty(),
                    mobile = existing?.mobile.orEmpty(),
                    geminiApiKey = existing?.geminiApiKey,
                    theme = existing?.theme,
                    imagePath = path,
                ),
            )
            _profilePhotoEpoch.value = Clock.System.now().toEpochMilliseconds()
            patchState { it.copy(userProfile = it.userProfile?.copy(imagePath = path) ?: UserProfile(
                id = Constants.SINGLETON_ID,
                name = "",
                email = "",
                mobile = "",
                imagePath = path,
            )) }
            toast("Profile photo updated")
        }
    }

    fun clearProfileImage() {
        val existing = _state.value.userProfile ?: return
        val path = existing.imagePath ?: repository.profileImageFilePath()
        deleteFileIfExists(path)
        repository.saveProfile(existing.copy(imagePath = null))
        _profilePhotoEpoch.value = Clock.System.now().toEpochMilliseconds()
        patchState { it.copy(userProfile = existing.copy(imagePath = null)) }
        toast("Profile photo removed")
    }

    fun setLocalMode(enabled: Boolean) {
        repository.setLocalMode(enabled)
        if (enabled) repository.setAuthChoice("local")
        refresh()
    }

    fun continueWithoutGoogle() = writeOnce("continueWithoutGoogle") {
        val localKey = Constants.ACCOUNT_KEY_LOCAL
        if (repository.hasParkedVault(localKey)) {
            runCatching { repository.restoreParkedVault(localKey) }
        }
        repository.setLocalMode(true)
        repository.setAuthChoice("local")
        repository.setAccountKey(localKey)
        repository.clearGoogleSession()
        refresh()
    }

    fun saveGoogleWebClientId(clientId: String) {
        repository.saveGoogleWebClientId(clientId)
        refresh()
        toast("Google client ID saved")
    }

    fun signInWithGoogle() {
        launchOnce("signInWithGoogle") {
            _isSigningIn.value = true
            _authError.value = null
            _driveRestoreOffer.value = null
            try {
                val before = _state.value
                val clientId = platformOAuthClientId(resolvedGoogleClientId())
                val user = googleAuth.signIn(clientId)
                val session = GoogleSession(
                    userId = user.userId,
                    email = user.email,
                    displayName = user.displayName,
                    imageUrl = user.imageUrl,
                    accessToken = user.accessToken,
                    refreshToken = user.refreshToken,
                    idToken = user.idToken,
                    expiresAtEpochMs = user.expiresAtEpochMs,
                )
                val googleKey = vaultAccountKey(user.userId)
                persistGoogleIdentity(session, googleKey)
                refreshNow()
                mergeGoogleProfile(user)
                refreshGoogleTokensQuietly()
                offerDriveRestoreIfPresent()
                refresh()
            } catch (error: GoogleAuthException) {
                _authError.value = humanizeGoogleSignInError(error.message)
            } catch (error: Exception) {
                if (_state.value.authChoice == "google") {
                    toast(error.message?.ifBlank { null } ?: "Signed in. Drive can be connected from Profile.")
                } else {
                    _authError.value = humanizeGoogleSignInError(error.message)
                }
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun acceptDriveRestore() {
        val file = _driveRestoreOffer.value ?: return
        launchOnce("acceptDriveRestore") {
            _isSigningIn.value = true
            try {
                runBackupAction("Restored this account from Google Drive.") {
                    withDriveAccess { token ->
                        val bytes = driveClient.downloadBackup(token, file.id)
                        withContext(Dispatchers.Default) { repository.restoreDatabaseBytes(bytes) }
                    }
                    val session = _state.value.googleSession
                    if (session != null) {
                        persistGoogleIdentity(session, vaultAccountKey(session.userId))
                    }
                    refreshNow()
                    session?.let { mergeGoogleProfile(googleUserFromSession(it)) }
                    if (hasUserFinanceData(_state.value) || _state.value.setupCompleted) {
                        repository.setSetupCompleted(true)
                    }
                    refresh()
                }
                if (_backupError.value == null) {
                    _driveRestoreOffer.value = null
                }
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun skipDriveRestore() = writeOnce("skipDriveRestore") {
        _driveRestoreOffer.value = null
        toast("Starting a fresh vault for this account")
    }

    fun signOut(backupFirst: Boolean) {
        if (!_isSigningOut.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                val snapshot = _state.value
                val key = snapshotAccountKey(snapshot)
                if (backupFirst) {
                    val backedUp = backupBeforeSignOut(snapshot)
                    if (!backedUp) return@launch
                }
                try {
                    googleAuth.signOut()
                } catch (_: Exception) {
                }
                repository.deleteParkedVault(key)
                repository.resetActiveVaultToEmpty()
                clearSessionUi()
                refresh()
                toast("Signed out. Local data erased.")
                _signedOut.emit(Unit)
            } catch (error: Exception) {
                toast(error.message?.ifBlank { null } ?: "Could not sign out")
            } finally {
                _isSigningOut.value = false
            }
        }
    }

    private suspend fun backupBeforeSignOut(snapshot: FinanceSnapshot): Boolean {
        return try {
            if (snapshot.googleSession != null) {
                withDriveAccess { token ->
                    val bytes = withContext(Dispatchers.Default) { repository.exportDatabaseBytes() }
                    driveClient.uploadBackup(token, backupFilename(), bytes)
                }
                repository.markBackup()
                toast("Vault backed up to Drive")
            } else {
                val bytes = withContext(Dispatchers.Default) { repository.exportDatabaseBytes() }
                fileActions.exportToUser(bytes, backupFilename())
                repository.markBackup()
                toast("Vault file saved")
            }
            true
        } catch (_: BackupCancelledException) {
            toast("Sign-out canceled")
            false
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            if (message.contains("cancel", ignoreCase = true)) {
                toast("Sign-out canceled")
                false
            } else {
                toast(error.message?.ifBlank { null } ?: "Backup failed. Sign-out canceled.")
                false
            }
        }
    }

    private suspend fun offerDriveRestoreIfPresent() {
        val backups = runCatching {
            withDriveAccess { token -> driveClient.listBackups(token) }
        }.onFailure {
            toast("Could not check Drive backups. Continuing with a fresh vault.")
        }.getOrDefault(emptyList())
        _cloudBackups.value = backups
        _driveRestoreOffer.value = backups.firstOrNull()
    }

    private fun persistGoogleIdentity(session: GoogleSession, googleKey: String) {
        val merged = overlayGoogleSession(session, _state.value.googleSession?.takeIf { it.userId == session.userId })
            ?: session
        repository.saveGoogleSession(merged)
        repository.setLocalMode(false)
        repository.setAuthChoice("google")
        repository.setAccountKey(googleKey)
    }

    private suspend fun refreshGoogleTokensQuietly() {
        val session = _state.value.googleSession ?: return
        runCatching {
            val tokens = googleAuth.freshAccessToken(
                clientId = platformOAuthClientId(resolvedGoogleClientId()),
                stored = StoredOAuth(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    idToken = session.idToken,
                    expiresAtEpochMs = session.expiresAtEpochMs,
                ),
                forceRefresh = session.accessToken.isNullOrBlank(),
            )
            persistOAuth(session, tokens)
        }
    }

    private fun mergeGoogleProfile(user: com.kashvault.auth.GoogleUser) {
        val existing = _state.value.userProfile
        repository.saveProfile(
            UserProfile(
                id = existing?.id ?: Constants.SINGLETON_ID,
                name = existing?.name?.takeIf { it.isNotBlank() } ?: user.displayName.orEmpty(),
                email = existing?.email?.takeIf { it.isNotBlank() } ?: user.email.orEmpty(),
                mobile = existing?.mobile.orEmpty(),
                geminiApiKey = existing?.geminiApiKey,
                theme = existing?.theme,
                imagePath = existing?.imagePath,
            ),
        )
    }

    private fun googleUserFromSession(session: GoogleSession) = com.kashvault.auth.GoogleUser(
        userId = session.userId,
        email = session.email,
        displayName = session.displayName,
        imageUrl = session.imageUrl,
        accessToken = session.accessToken,
        refreshToken = session.refreshToken,
        idToken = session.idToken,
        expiresAtEpochMs = session.expiresAtEpochMs,
    )

    private fun clearSessionUi() {
        _advisorReply.value = null
        _advisorMessages.value = emptyList()
        _liquidityForecast.value = null
        _cloudBackups.value = emptyList()
        _driveRestoreOffer.value = null
        _inboxNotice.value = null
        _backupMessage.value = null
        _backupError.value = null
        _authError.value = null
    }

    fun resolvedGoogleClientId(): String {
        val stored = _state.value.googleWebClientId.trim()
        if (stored.isNotEmpty()) return stored
        return bundledGoogleClientId()
    }

    fun getBudgetUsage(): BudgetUsageSummary {
        val s = _state.value
        return getCategoryBudgetUsage(s.categories, s.budgets, s.expenses)
    }

    fun getNetWorthBreakdown(): NetWorthBreakdown {
        val s = _state.value
        return getNetWorthBreakdown(
            NetWorthInput(
                expenses = s.expenses,
                investments = s.investments,
                emergencyFund = s.emergencyFund,
                savingsGoals = s.savingsGoals,
                borrowLend = s.borrowLend,
                loans = s.loans,
                paymentSources = s.paymentSources,
            ),
        )
    }

    fun getPortfolioIntegrity(): PortfolioIntegrityReport =
        auditPortfolioIntegrity(_state.value)

    fun getLendingTotals(): LendingTotals = getLendingTotals(_state.value.borrowLend, _state.value.people)

    fun getGlobalLiquidity(): Double = getGlobalLiquidity(_state.value.expenses)

    fun getLienAmount(): Double {
        val s = _state.value
        return getLienAmount(s.investments, s.insurances, s.loans, bills = s.recurringBills)
    }

    fun getLiquidityInput(): LiquidityInput {
        val s = _state.value
        val monthly = getFilteredExpenses(s.expenses, "monthly")
        val spend = monthly.filter { it.isSpend() }.sumOf { it.amount }
        val income = monthly.filter { it.isIncome() }.sumOf { it.amount }
        val cash = if (s.paymentSources.any { it.kind != Constants.SOURCE_CREDIT_CARD }) {
            availableCash(s.paymentSources, s.expenses)
        } else {
            getGlobalLiquidity(s.expenses)
        }
        return LiquidityInput(
            currentLiquidity = cash,
            monthlyIncome = income,
            monthlyAvgSpend = if (spend > 0) spend else 1.0,
            upcomingLien = getLienAmount(),
        )
    }

    fun runLiquidityForecast(useGemini: Boolean = true) {
        launchOnce("runLiquidityForecast") {
            _isForecasting.value = true
            val input = getLiquidityInput()
            val heuristic = heuristicLiquidityForecast(input)
            val apiKey = if (useGemini) _state.value.userProfile?.geminiApiKey.orEmpty().trim() else ""
            val model = resolveGeminiModel(_state.value.geminiModel)
            val ai = if (apiKey.isNotBlank()) {
                runCatching {
                    withContext(Dispatchers.Default) {
                        geminiClient.forecastLiquidity(apiKey, input, preferredModel = model)
                    }
                }.onFailure { error ->
                    toast(geminiFailureMessage(error))
                }.getOrNull()
            } else {
                null
            }
            _liquidityForecast.value = ai ?: heuristic
            _isForecasting.value = false
        }
    }

    fun magicLog(text: String, onResult: (Boolean) -> Unit) {
        launchOnce("magicLog") job@{
            val s = _state.value
            val apiKey = s.userProfile?.geminiApiKey.orEmpty().trim()
            if (apiKey.isBlank()) {
                onResult(false)
                return@job
            }
            val categories = s.categories.map { it.id to it.name }
            val sources = s.paymentSources.map { it.id to "${it.kindLabel()} ${it.displayName()}" }
            val parsed = runCatching {
                withContext(Dispatchers.Default) {
                    geminiClient.parseExpense(
                        apiKey,
                        text,
                        categories,
                        sources,
                        preferredModel = resolveGeminiModel(s.geminiModel),
                    )
                }
            }.onFailure { error ->
                toast(geminiFailureMessage(error))
            }.getOrNull()
            if (parsed != null) {
                val amount = parsed["amount"]?.toDoubleOrNull() ?: run {
                    onResult(false)
                    return@job
                }
                val categoryId = parsed["categoryId"]
                    ?: s.categories.firstOrNull()?.id
                    ?: INITIAL_CATEGORIES.first().id
                val note = parsed["note"]
                val type = parsed["type"] ?: "expense"
                val sourceId = parsed["sourceId"]?.takeIf { id -> s.paymentSources.any { it.id == id } }
                    ?: s.lastPaymentSourceId
                onResult(addExpense(amount, categoryId, note, type, sourceId))
            } else {
                onResult(false)
            }
        }
    }

    fun notificationCaptureSupported(): Boolean = inboxCapture.notificationCaptureSupported()

    fun smsCaptureSupported(): Boolean = inboxCapture.smsCaptureSupported()

    fun notificationInboxEnabled(): Boolean = _notificationAccessGranted.value

    fun openNotificationInboxSettings() {
        inboxCapture.openNotificationAccessSettings()
        refreshInboxCapturePermissions()
    }

    fun smsInboxEnabled(): Boolean = _smsPermissionGranted.value

    fun dismissSmsPermissionPrompt() {
        _smsPermissionPrompt.value = false
    }

    fun confirmSmsPermissionPrompt() {
        _smsPermissionPrompt.value = false
        scanSmsInbox(afterPrompt = true)
    }

    fun scanSmsInbox(afterPrompt: Boolean = false) {
        if (!_isScanningSms.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                _smsScanProgress.value = "Scanning"
                val granted = if (inboxCapture.smsPermissionGranted()) {
                    true
                } else {
                    inboxCapture.requestSmsPermission()
                }
                refreshInboxCapturePermissions()
                if (!granted) {
                    askOrOpenSmsPermission(afterPrompt)
                    return@launch
                }
                toast("SMS scan started")
                val result = withContext(Dispatchers.Default) {
                    val outcome = inboxCapture.scanRecentSms { progress ->
                        _smsScanProgress.value = if (progress.added == 0) {
                            "Scanning"
                        } else {
                            "${progress.added} found"
                        }
                        refreshNow()
                    }
                    refreshNow()
                    outcome
                }
                if (result.accessDenied) {
                    val retried = inboxCapture.requestSmsPermission()
                    refreshInboxCapturePermissions()
                    if (retried && inboxCapture.smsPermissionGranted()) {
                        val again = withContext(Dispatchers.Default) {
                            val outcome = inboxCapture.scanRecentSms { progress ->
                                _smsScanProgress.value = if (progress.added == 0) {
                                    "Scanning"
                                } else {
                                    "${progress.added} found"
                                }
                                refreshNow()
                            }
                            refreshNow()
                            outcome
                        }
                        if (!again.accessDenied) {
                            inboxFeedback(
                                if (again.added == 0) "No new SMS payments found."
                                else "Added ${again.added} SMS item(s) to the inbox.",
                            )
                            return@launch
                        }
                    }
                    askOrOpenSmsPermission(afterPrompt)
                    return@launch
                }
                inboxFeedback(
                    if (result.added == 0) "No new SMS payments found."
                    else "Added ${result.added} SMS item(s) to the inbox.",
                )
            } finally {
                _isScanningSms.value = false
                _smsScanProgress.value = null
            }
        }
    }

    private fun askOrOpenSmsPermission(afterPrompt: Boolean) {
        if (afterPrompt) {
            inboxCapture.openSmsPermissionSettings()
            inboxFeedback("Turn on SMS for KashVault, then return here and tap Scan. You can keep using the rest of the app.")
        } else {
            _smsPermissionPrompt.value = true
        }
    }

    fun ingestPastedInbox(text: String) = writeOnce("ingestPastedInbox") {
        val body = text.trim()
        if (body.isEmpty()) return
        val added = repository.ingestCapturedMessage(Constants.INBOX_PASTE, "paste", body) != null
        refresh()
        inboxFeedback(
            if (added) {
                "Parsed the pasted message."
            } else {
                "That message was already processed."
            },
        )
    }

    fun inboxDueMatch(amount: Double, type: String, suggestedSourceId: String?): DuePostCandidate? =
        matchInboxToDue(amount, type, suggestedSourceId, openDueCandidates(includeFuture = true))

    fun inboxCardBillMatch(item: InboxItem): CardBillMatch? =
        matchInboxToCardBill(
            amount = item.amount,
            suggestedSourceId = item.suggestedSourceId,
            sender = item.sender,
            body = item.body,
            sources = _state.value.paymentSources,
            expenses = _state.value.expenses,
            lastPaymentSourceId = _state.value.lastPaymentSourceId,
        )

    fun dueTimelineItems() = buildDueTimeline(
        investments = _state.value.investments,
        insurances = _state.value.insurances,
        loans = _state.value.loans,
        bills = _state.value.recurringBills,
        sources = _state.value.paymentSources,
        expenses = _state.value.expenses,
    )

    fun updateInboxDraft(id: String, categoryId: String?, sourceId: String?, note: String?) {
        repository.updateInboxSuggestion(id, categoryId, sourceId, note)
        refresh()
    }

    private fun rememberInboxCapture(
        item: InboxItem?,
        categoryId: String?,
        sourceId: String?,
        note: String?,
    ) {
        if (item == null) return
        quietly {
            repository.learnCaptureFromInbox(
                item.copy(
                    suggestedCategoryId = categoryId ?: item.suggestedCategoryId,
                    suggestedSourceId = sourceId ?: item.suggestedSourceId,
                    note = note ?: item.note,
                ),
            )
        }
    }

    fun acceptInbox(
        id: String,
        categoryId: String?,
        sourceId: String?,
        note: String?,
        timestamp: String? = null,
        dueKind: String? = null,
        dueEntityId: String? = null,
        cardId: String? = null,
        amount: Double? = null,
        type: String? = null,
    ) {
        val hidden = _state.value.inboxItems.find { it.id == id }
        val effectiveAmount = amount?.takeIf { it.isFinite() && it > 0 } ?: hidden?.amount
        repository.updateInboxSuggestion(id, categoryId, sourceId, note)
        if (!cardId.isNullOrBlank()) {
            val from = sourceId?.takeIf { it.isNotBlank() } ?: hidden?.suggestedSourceId
            val expenseId = generateId()
            val paid = if (from.isNullOrBlank()) {
                false
            } else {
                quietly { payCardBill(cardId, from, effectiveAmount, timestamp, id = expenseId) }
            }
            if (paid) {
                removeInboxFromUi(id)
                repository.markInboxAccepted(id)
                rememberInboxCapture(hidden, categoryId, sourceId, note)
                toastUndo(
                    "Posted as a card bill payment.",
                    InboxUndoAction(inboxId = id, item = hidden, expenseId = expenseId),
                )
                refreshNow()
            } else {
                inboxFeedback("Couldn’t post that card bill. Pick a bank and try again.")
            }
            return
        }
        if (!dueKind.isNullOrBlank() && !dueEntityId.isNullOrBlank()) {
            val paid = quietly { markDuePaid(dueKind, dueEntityId, sourceId) }
            if (paid) {
                removeInboxFromUi(id)
                repository.markInboxAccepted(id)
                rememberInboxCapture(hidden, categoryId, sourceId, note)
                toastUndo(
                    "Posted against the matching due.",
                    InboxUndoAction(inboxId = id, item = hidden, dueKind = dueKind, dueEntityId = dueEntityId),
                )
                refreshNow()
            }
            return
        }
        val posted = repository.acceptInboxItem(
            id = id,
            timestamp = timestamp,
            amount = effectiveAmount,
            type = type,
            categoryId = categoryId,
            sourceId = sourceId,
            note = note,
        )
        if (posted != null) {
            removeInboxFromUi(id)
            rememberInboxCapture(
                hidden,
                categoryId ?: posted.categoryId,
                sourceId ?: posted.sourceId,
                note ?: posted.note,
            )
            toastUndo(
                inboxPostedFeedback(posted),
                InboxUndoAction(inboxId = id, item = hidden, expenseId = posted.id),
            )
            refreshNow()
        } else {
            inboxFeedback(inboxPostedFeedback(null))
        }
    }

    fun dismissInbox(id: String) {
        val hidden = removeInboxFromUi(id)
        repository.dismissInboxItem(id)
        toastUndo("Dismissed", InboxUndoAction(inboxId = id, item = hidden))
    }

    private fun undoInboxAction(undo: InboxUndoAction) {
        quietly {
            if (!undo.dueKind.isNullOrBlank() && !undo.dueEntityId.isNullOrBlank()) {
                undoDue(undo.dueKind, undo.dueEntityId)
            } else if (!undo.expenseId.isNullOrBlank()) {
                val previous = _state.value.expenses.find { it.id == undo.expenseId }
                repository.deleteExpense(undo.expenseId)
                syncTransferDestinations(previous, null)
            }
            repository.restoreInboxPending(undo.inboxId)
        }
        undo.item?.let { restoreInboxInUi(it) }
        refreshNow()
    }

    private fun removeInboxFromUi(id: String): InboxItem? {
        val item = _state.value.inboxItems.find { it.id == id }
        patchState { it.copy(inboxItems = it.inboxItems.filter { row -> row.id != id }) }
        return item
    }

    private fun restoreInboxInUi(item: InboxItem) {
        patchState { state ->
            val pending = item.copy(status = Constants.INBOX_PENDING)
            val rows = (state.inboxItems.filter { it.id != pending.id } + pending)
                .sortedByDescending { it.capturedAt }
            state.copy(inboxItems = rows)
        }
    }

    fun clearInboxNotice() {
        _inboxNotice.value = null
    }

    fun askAdvisor(message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        launchOnce("askAdvisor") {
            _isAskingAdvisor.value = true
            _advisorMessages.value = _advisorMessages.value + AdvisorChatMessage("user", trimmed)
            val s = _state.value
            val breakdown = getNetWorthBreakdown()
            val lending = getLendingTotals()
            val apiKey = s.userProfile?.geminiApiKey.orEmpty().trim()
            val context = """
                    User finances snapshot:
                    Net worth: ${formatINR(breakdown.netWorth)}
                    Liquidity: ${formatINR(breakdown.liquidity)}
                    Investments: ${formatINR(breakdown.investments)}
                    Loan liability: ${formatINR(breakdown.loanLiability)}
                    Emergency: ${formatINR(s.emergencyFund.currentSaved)}
                    Lending net: ${formatINR(lending.overall)}
                    Monthly expenses tracked: ${s.expenses.filter { it.isSpend() }.size} transactions
                """.trimIndent()
            val local = {
                heuristicAdvisorReply(
                    question = trimmed,
                    breakdown = breakdown,
                    lending = lending,
                    emergency = s.emergencyFund,
                    loans = s.loans,
                    goals = s.savingsGoals,
                    expenses = s.expenses,
                    categories = s.categories,
                    hasGeminiKey = apiKey.isNotBlank(),
                )
            }
            try {
                val reply = if (apiKey.isNotBlank()) {
                    withContext(Dispatchers.Default) {
                        geminiClient.generateText(
                            apiKey,
                            trimmed,
                            context,
                            preferredModel = s.geminiModel,
                        )
                    } ?: local()
                } else {
                    local()
                }
                _advisorReply.value = reply
                _advisorMessages.value = _advisorMessages.value + AdvisorChatMessage("assistant", reply)
            } catch (error: Exception) {
                val reason = geminiFailureMessage(error)
                toast(if (reason.contains("timed out", ignoreCase = true)) reason else "Gemini could not answer")
                val reply = buildString {
                    appendLine(reason)
                    appendLine()
                    append(local())
                }
                _advisorReply.value = reply
                _advisorMessages.value = _advisorMessages.value + AdvisorChatMessage("assistant", reply)
            } finally {
                _isAskingAdvisor.value = false
            }
        }
    }

    fun clearAdvisorReply() {
        _advisorReply.value = null
        _advisorMessages.value = emptyList()
    }

    fun backupToDrive() {
        launchOnce("vaultBackup") {
            runBackupAction("Backed up the SQLite vault to your private Google Drive app folder.") {
                withDriveAccess { token ->
                    val bytes = withContext(Dispatchers.Default) { repository.exportDatabaseBytes() }
                    val filename = backupFilename()
                    driveClient.uploadBackup(token, filename, bytes)
                }
                repository.markBackup()
                refreshCloudBackups()
                refresh()
            }
        }
    }

    fun restoreLatestFromDrive() {
        launchOnce("vaultBackup") {
            runBackupAction("Restored the SQLite vault from Google Drive.") {
                withDriveAccess { token ->
                    val latest = driveClient.listBackups(token).firstOrNull()
                        ?: throw DatabaseBackupException("No database backups found in Google Drive.")
                    val bytes = driveClient.downloadBackup(token, latest.id)
                    withContext(Dispatchers.Default) { repository.restoreDatabaseBytes(bytes) }
                }
                refresh()
                refreshCloudBackups()
            }
        }
    }

    fun restoreFromDrive(fileId: String) {
        launchOnce("vaultBackup") {
            runBackupAction("Restored the SQLite vault from Google Drive.") {
                withDriveAccess { token ->
                    val bytes = driveClient.downloadBackup(token, fileId)
                    withContext(Dispatchers.Default) { repository.restoreDatabaseBytes(bytes) }
                }
                refresh()
            }
        }
    }

    fun refreshCloudBackups() {
        scope.launch {
            try {
                _cloudBackups.value = withDriveAccess { token -> driveClient.listBackups(token) }
            } catch (_: Exception) {
                _cloudBackups.value = emptyList()
            }
        }
    }

    fun exportDatabaseFile() {
        launchOnce("vaultBackup") {
            runBackupAction("Saved a copy of kashvault.db on this device.") {
                val bytes = withContext(Dispatchers.Default) { repository.exportDatabaseBytes() }
                fileActions.exportToUser(bytes, backupFilename())
                repository.markBackup()
                refresh()
            }
        }
    }

    fun eraseLocalData(eraseDriveBackups: Boolean) {
        launchOnce("vaultBackup") {
            runBackupAction(
                if (eraseDriveBackups) {
                    "Erased local data and Google Drive backups."
                } else {
                    "Erased local data. Google Drive backups were kept."
                },
            ) {
                if (eraseDriveBackups) {
                    withDriveAccess { token -> driveClient.deleteAllBackups(token) }
                }
                try {
                    withContext(Dispatchers.Default) { repository.eraseAllLocalData() }
                } catch (error: Exception) {
                    try {
                        googleAuth.signOut()
                    } catch (_: Exception) {
                    }
                    clearSessionUi()
                    refresh()
                    _signedOut.emit(Unit)
                    val driveStatus = if (eraseDriveBackups) {
                        "Google Drive backups were deleted, but local data could not be fully erased."
                    } else {
                        "Local data could not be fully erased."
                    }
                    throw DatabaseBackupException("$driveStatus ${error.message.orEmpty()}")
                }
                try {
                    googleAuth.signOut()
                } catch (_: Exception) {
                }
                clearSessionUi()
                refresh()
                _signedOut.emit(Unit)
            }
        }
    }

    fun importDatabaseFile() {
        launchOnce("vaultBackup") job@{
            _isBackingUp.value = true
            _backupError.value = null
            _backupMessage.value = null
            try {
                val bytes = fileActions.importFromUser() ?: return@job
                assertSqliteDatabase(bytes)
                withContext(Dispatchers.Default) { repository.restoreDatabaseBytes(bytes) }
                refresh()
                val restored = "Restored the vault from a SQLite database file."
                _backupMessage.value = restored
                toast(restored)
            } catch (_: BackupCancelledException) {
                _backupMessage.value = null
                _backupError.value = null
            } catch (error: Exception) {
                val failure = error.message ?: "Restore failed."
                _backupError.value = failure
                toast(failure)
            } finally {
                _isBackingUp.value = false
            }
        }
    }

    private suspend fun runBackupAction(success: String, block: suspend () -> Unit) {
        _isBackingUp.value = true
        _backupError.value = null
        _backupMessage.value = null
        try {
            block()
            _backupMessage.value = success
            toast(success)
        } catch (_: BackupCancelledException) {
            _backupMessage.value = null
            _backupError.value = null
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            if (message.contains("cancel", ignoreCase = true)) {
                _backupMessage.value = null
                _backupError.value = null
            } else {
                val failure = error.message ?: "Backup failed."
                _backupError.value = failure
                toast(failure)
            }
        } finally {
            _isBackingUp.value = false
        }
    }

    private suspend fun <T> withDriveAccess(block: suspend (String) -> T): T {
        return try {
            block(requireDriveToken())
        } catch (error: Exception) {
            if (!isGoogleAuthFailure(error)) throw error
            block(requireDriveToken(forceRefresh = true))
        }
    }

    private fun isGoogleAuthFailure(error: Exception): Boolean {
        val message = error.message.orEmpty()
        return message.contains("(401)") ||
            message.contains("(403)") ||
            message.contains("invalid_grant", ignoreCase = true) ||
            message.contains("UNAUTHENTICATED")
    }

    private suspend fun requireDriveToken(forceRefresh: Boolean = false): String {
        val session = _state.value.googleSession
            ?: throw DatabaseBackupException("Sign in with Google to use private Drive database backups.")
        val clientId = platformOAuthClientId(resolvedGoogleClientId())
        val tokens = googleAuth.freshAccessToken(
            clientId = clientId,
            stored = StoredOAuth(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                idToken = session.idToken,
                expiresAtEpochMs = session.expiresAtEpochMs,
            ),
            forceRefresh = forceRefresh,
        )
        persistOAuth(session, tokens)
        return tokens.accessToken
    }

    private fun persistOAuth(session: GoogleSession, tokens: OAuthTokens) {
        val refreshToken = tokens.refreshToken ?: session.refreshToken
        val idToken = tokens.idToken ?: session.idToken
        if (
            tokens.accessToken == session.accessToken &&
            refreshToken == session.refreshToken &&
            tokens.expiresAtEpochMs == session.expiresAtEpochMs
        ) {
            return
        }
        repository.saveGoogleSession(
            session.copy(
                accessToken = tokens.accessToken,
                refreshToken = refreshToken,
                idToken = idToken,
                expiresAtEpochMs = tokens.expiresAtEpochMs,
            ),
        )
        refresh()
    }

    private fun processDueItemsInternal() {
        openDueCandidates(includeFuture = false).forEach { candidate ->
            val sourceId = resolveDuePaymentSourceId(candidate.itemSourceId, _state.value.lastPaymentSourceId)
                ?: return@forEach
            postDueCandidate(candidate, sourceId)
            refreshNow()
        }
    }

    private fun openDueCandidates(includeFuture: Boolean): List<DuePostCandidate> {
        val snapshot = _state.value
        return collectDuePostCandidates(
            investments = snapshot.investments,
            insurances = snapshot.insurances,
            loans = snapshot.loans,
            includeFuture = includeFuture,
            bills = snapshot.recurringBills,
            sources = snapshot.paymentSources,
            expenses = snapshot.expenses,
        )
    }

    private fun postDueCandidate(candidate: DuePostCandidate, sourceId: String) {
        val processed = monthKey(candidate.dueOn)
        val categoryId = candidate.categoryId?.let { id -> _state.value.categories.find { it.id == id }?.id }
            ?: _state.value.categories.find { it.id == dueCategoryId(candidate.kind) }?.id
            ?: _state.value.categories.firstOrNull { if (candidate.expenseType == "income") it.type == "income" else it.type != "income" }?.id
            ?: INITIAL_CATEGORIES.first().id
        val note = when (candidate.kind) {
            DUE_KIND_SIP -> "SIP · ${candidate.title}"
            DUE_KIND_INSURANCE -> "Insurance · ${candidate.title}"
            DUE_KIND_EMI -> "EMI · ${candidate.title}"
            DUE_KIND_SALARY -> "Salary · ${candidate.title}"
            DUE_KIND_CARD -> "Card bill · ${candidate.title}"
            DUE_KIND_BILL -> "Bill · ${candidate.title}"
            else -> candidate.title
        }
        repository.addExpense(
            Expense(
                id = generateId(),
                amount = candidate.amount,
                categoryId = categoryId,
                timestamp = timestampOnDate(candidate.dueOn),
                note = note,
                type = candidate.expenseType,
                isAutoGenerated = true,
                sourceId = sourceId,
                destinationId = candidate.destinationId,
                dueKind = candidate.kind,
                dueEntityId = candidate.entityId,
            ),
        )
        if (candidate.expenseType == Constants.TYPE_TRANSFER) {
            // Card bill is a transfer; bank/card balances follow source/destination on the expense.
        }
        repository.setLastPaymentSourceId(sourceId)
        markDueProcessed(candidate, processed, postedSourceId = sourceId, posted = true)
    }

    private fun markDueProcessed(candidate: DuePostCandidate, processed: String, postedSourceId: String?, posted: Boolean) {
        when (candidate.kind) {
            DUE_KIND_SIP -> {
                repository.saveInvestments(
                    _state.value.investments.map { inv ->
                        if (inv.id != candidate.entityId) inv
                        else inv.copy(
                            investedAmount = (inv.investedAmount ?: 0.0) + if (posted) inv.amount else 0.0,
                            lastProcessedMonth = processed,
                            sourceId = inv.sourceId ?: postedSourceId,
                        )
                    },
                )
            }
            DUE_KIND_INSURANCE -> {
                repository.saveInsurances(
                    _state.value.insurances.map { ins ->
                        if (ins.id != candidate.entityId) ins
                        else ins.copy(lastProcessedMonth = processed, sourceId = ins.sourceId ?: postedSourceId)
                    },
                )
            }
            DUE_KIND_EMI -> {
                repository.saveLoans(
                    _state.value.loans.map { loan ->
                        if (loan.id != candidate.entityId) loan
                        else if (posted) applyEmiPayment(loan.copy(sourceId = loan.sourceId ?: postedSourceId), processed)
                        else loan.copy(lastProcessedMonth = processed, sourceId = loan.sourceId ?: postedSourceId)
                    },
                )
            }
            DUE_KIND_BILL, DUE_KIND_SALARY -> {
                repository.saveRecurringBills(
                    _state.value.recurringBills.map { bill ->
                        if (bill.id != candidate.entityId) bill
                        else bill.copy(lastProcessedMonth = processed, sourceId = bill.sourceId ?: postedSourceId)
                    },
                )
            }
            DUE_KIND_CARD -> {
                _state.value.paymentSources.find { it.id == candidate.entityId }?.let { card ->
                    repository.savePaymentSource(
                        card.copy(
                            lastProcessedMonth = processed,
                            payFromSourceId = card.payFromSourceId ?: postedSourceId,
                        ),
                    )
                }
            }
        }
    }

    private fun syncTransferDestinations(previous: Expense?, next: Expense?) {
        var fund = _state.value.emergencyFund
        var goals = _state.value.savingsGoals
        fun apply(destinationId: String?, delta: Double) {
            when {
                destinationId == Constants.DEST_EMERGENCY -> {
                    fund = fund.copy(currentSaved = roundCurrency((fund.currentSaved + delta).coerceAtLeast(0.0)))
                }
                destinationId != null -> {
                    val goalId = Constants.goalIdFromDestination(destinationId) ?: return
                    goals = goals.map { goal ->
                        if (goal.id != goalId) goal
                        else {
                            val current = roundCurrency((goal.current + delta).coerceAtLeast(0.0))
                            goal.copy(current = current, completed = current >= goal.target)
                        }
                    }
                }
            }
        }
        if (previous?.isTransfer() == true) apply(previous.destinationId, -roundCurrency(previous.amount))
        if (next?.isTransfer() == true) apply(next.destinationId, roundCurrency(next.amount))
        val fundChanged = fund != _state.value.emergencyFund
        val goalsChanged = goals != _state.value.savingsGoals
        if (fundChanged) repository.saveEmergencyFund(fund)
        if (goalsChanged) repository.saveSavingsGoals(goals)
        if (fundChanged || goalsChanged) {
            patchState { it.copy(emergencyFund = fund, savingsGoals = goals) }
        }
    }

    private fun patchLending(item: BorrowLend, updated: Boolean, person: Person) {
        patchState { state ->
            val logs = if (updated) {
                state.borrowLend.map { if (it.id == item.id) item else it }
            } else {
                listOf(item) + state.borrowLend.filter { it.id != item.id }
            }
            val people = if (state.people.any { it.id == person.id }) {
                state.people.map { if (it.id == person.id) person else it }
            } else {
                state.people + person
            }
            state.copy(borrowLend = logs, people = people)
        }
    }

    private fun resolvePerson(name: String, personId: String?, phone: String?): Person {
        val trimmed = name.trim()
        val existing = personId?.let { id -> _state.value.people.find { it.id == id } }
            ?: _state.value.people.find { it.name.equals(trimmed, ignoreCase = true) }
        val person = (existing ?: Person(id = generateId(), name = trimmed, phone = phone?.ifBlank { null })).let { row ->
            if (!phone.isNullOrBlank() && row.phone != phone) row.copy(phone = phone) else row
        }
        if (existing == null || person != existing) {
            repository.savePerson(person)
        }
        return person
    }

    private fun transferCategoryId(): String =
        _state.value.categories.find { it.id == "cat_wealth" }?.id
            ?: _state.value.categories.firstOrNull { it.type != "income" }?.id
            ?: INITIAL_CATEGORIES.first().id

    private fun backupFilename(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val stamp = listOf(
            now.year.toString().padStart(4, '0'),
            now.monthNumber.toString().padStart(2, '0'),
            now.dayOfMonth.toString().padStart(2, '0'),
            now.hour.toString().padStart(2, '0'),
            now.minute.toString().padStart(2, '0'),
            now.second.toString().padStart(2, '0'),
        ).joinToString("")
        return "$VAULT_BACKUP_PREFIX$stamp$VAULT_BACKUP_SUFFIX"
    }

    private fun generateId(): String =
        "${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(10000)}"
}
