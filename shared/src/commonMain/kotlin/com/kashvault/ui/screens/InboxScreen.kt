package com.kashvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashvault.ui.rememberSingleClick
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.domain.InboxItem
import com.kashvault.domain.banks
import com.kashvault.domain.creditCards
import com.kashvault.domain.displayLabel
import com.kashvault.domain.displayName
import com.kashvault.domain.kindLabel
import com.kashvault.domain.preferredExpenseCategoryId
import com.kashvault.inbox.extractAccountIdentifier
import com.kashvault.inbox.extractInstrumentType
import com.kashvault.inbox.inboxAlreadyInHistory
import com.kashvault.inbox.inboxCaptureReason
import com.kashvault.inbox.inboxCategoryBadge
import com.kashvault.inbox.inboxFilterMatches
import com.kashvault.inbox.inboxMerchantHint
import com.kashvault.inbox.isCardBillPayment
import com.kashvault.inbox.isCardSpend
import com.kashvault.inbox.learnableKeywordToken
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.DatePickerField
import com.kashvault.ui.components.EmptyState
import com.kashvault.ui.components.FilterIconButton
import com.kashvault.ui.components.FilterRow
import com.kashvault.ui.components.FilterSection
import com.kashvault.ui.components.FilterSheet
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.MerchantMark
import com.kashvault.ui.components.MoneyText
import com.kashvault.ui.components.PickerOption
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.SearchablePickerField
import com.kashvault.ui.components.SecondaryButton
import com.kashvault.ui.components.SoftChip
import com.kashvault.ui.haptics.platformHapticTick
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.CardRadius
import com.kashvault.ui.theme.ScreenContentPadding
import com.kashvault.ui.theme.ScreenGap
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.formatINR
import com.kashvault.util.formatTimestamp
import com.kashvault.util.parseLocalDateOrNull
import com.kashvault.util.timestampOnDate
import com.kashvault.util.todayLocalDate
import kotlinx.coroutines.delay

object InboxScreen : Screen {
    override val key = "inbox"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val notice by vm.inboxNotice.collectAsState()
        val scanningSms by vm.isScanningSms.collectAsState()
        val scanProgress by vm.smsScanProgress.collectAsState()
        val alertsOn by vm.notificationAccessGranted.collectAsState()
        val smsOn by vm.smsPermissionGranted.collectAsState()
        val smsPrompt by vm.smsPermissionPrompt.collectAsState()

        var pasteText by remember { mutableStateOf("") }
        var chipFilter by remember { mutableStateOf("all") }
        var showFilters by remember { mutableStateOf(false) }
        var adjusting by remember { mutableStateOf<InboxItem?>(null) }
        val clicks = rememberSingleClick()

        LaunchedEffect(Unit) {
            while (true) {
                vm.refreshInboxCapturePermissions()
                vm.refresh()
                delay(1500)
            }
        }

        if (isLoading) {
            LoadingScreen()
            return
        }

        val pending = state.inboxItems.filter { inboxFilterMatches(it, chipFilter, state.categories) }
        val banks = state.paymentSources.banks()
        val cards = state.paymentSources.creditCards()
        val chipOptions = listOf(
            "all" to "All",
            "pending" to "Pending",
            "upi" to "UPI",
            "food" to "Food",
            "grocery" to "Grocery",
            "shopping" to "Shopping",
            "salary" to "Salary",
            "fuel" to "Fuel",
        )

        KashVaultScaffold(
            currentScreen = this,
            title = "Inbox",
        ) { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = ScreenContentPadding,
                verticalArrangement = Arrangement.spacedBy(ScreenGap),
            ) {
                if (notice != null) {
                    item {
                        KashCard {
                            Text(notice!!, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.clearInboxNotice() }) { Text("Dismiss") }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.notificationCaptureSupported()) {
                            AssistChip(
                                onClick = { vm.openNotificationInboxSettings() },
                                label = { Text(if (alertsOn) "Alerts on" else "Allow alerts") },
                                leadingIcon = {
                                    Icon(
                                        if (alertsOn) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                        if (vm.smsCaptureSupported()) {
                            AssistChip(
                                onClick = { vm.scanSmsInbox() },
                                enabled = !scanningSms,
                                label = {
                                    Text(
                                        when {
                                            scanningSms -> "Scanning"
                                            smsOn -> "Scan SMS"
                                            else -> "Allow SMS"
                                        },
                                    )
                                },
                                leadingIcon = {
                                    if (scanningSms) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        FilterIconButton(
                            activeCount = if (chipFilter != "all") 1 else 0,
                            onClick = { showFilters = true },
                        )
                    }
                }

                item {
                    VaultOutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste a bank SMS or UPI message") },
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    clicks("paste") {
                                        vm.ingestPastedInbox(pasteText)
                                        pasteText = ""
                                    }
                                },
                                enabled = pasteText.isNotBlank(),
                            ) { Text("Parse") }
                        },
                    )
                }

                if (pending.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (state.inboxItems.isEmpty()) "Inbox is clear" else "Nothing for this filter",
                            body = if (state.inboxItems.isEmpty()) {
                                "Paste a bank SMS, scan SMS, or turn on notification access. Messages stay on this phone."
                            } else {
                                "Try All to see other payments."
                            },
                        )
                    }
                } else {
                    items(pending, key = { it.id }) { item ->
                        SwipeInboxRow(
                            item = item,
                            categories = state.categories,
                            sources = state.paymentSources,
                            reason = inboxCaptureReason(
                                item,
                                state.categories,
                                state.paymentSources,
                                state.smsScanKeywords,
                            ),
                            alreadyBooked = inboxAlreadyInHistory(item, state.expenses),
                            onAccept = {
                                vm.acceptInbox(
                                    item.id,
                                    item.suggestedCategoryId,
                                    item.suggestedSourceId,
                                    item.note,
                                    item.capturedAt,
                                    dueKind = null,
                                    dueEntityId = null,
                                    cardId = null,
                                )
                            },
                            onDismiss = { vm.dismissInbox(item.id) },
                            onOpen = { adjusting = item },
                        )
                    }
                }
            }
        }

        FilterSheet(
            visible = showFilters,
            onDismiss = { showFilters = false },
            onReset = { chipFilter = "all" },
        ) {
            FilterSection("Inbox") {
                FilterRow(options = chipOptions, selected = chipFilter, onSelect = { chipFilter = it })
            }
        }

        adjusting?.let { item ->
            val cardMatch = vm.inboxCardBillMatch(item)
            val dueMatch = if (cardMatch == null) {
                vm.inboxDueMatch(item.amount, item.type, item.suggestedSourceId)
            } else {
                null
            }
            InboxAdjustSheet(
                item = item,
                categories = state.categories,
                banks = banks,
                cards = cards,
                dueMatchTitle = dueMatch?.let { "${it.title} (${it.kind})" },
                cardBillTitle = cardMatch?.let { "Pay ${it.cardName} from ${it.bankName}" },
                preferredBankId = cardMatch?.bankId,
                onDismissSheet = { adjusting = null },
                onAccept = { categoryId, sourceId, note, timestamp, applyDue, applyCardBill, amount, type ->
                    vm.acceptInbox(
                        item.id,
                        categoryId,
                        sourceId,
                        note,
                        timestamp,
                        dueKind = if (applyDue && cardMatch == null) dueMatch?.kind else null,
                        dueEntityId = if (applyDue && cardMatch == null) dueMatch?.entityId else null,
                        cardId = if (applyCardBill) cardMatch?.cardId else null,
                        amount = amount,
                        type = type,
                    )
                    adjusting = null
                },
                onDismissItem = {
                    vm.dismissInbox(item.id)
                    adjusting = null
                },
            )
        }

        if (smsPrompt) {
            AlertDialog(
                onDismissRequest = { vm.dismissSmsPermissionPrompt() },
                title = { Text("Allow SMS") },
                text = {
                    Text("This phone blocked SMS for KashVault. Allow SMS so Scan can read bank messages.")
                },
                confirmButton = {
                    TextButton(onClick = { vm.confirmSmsPermissionPrompt() }) { Text("Allow SMS") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissSmsPermissionPrompt() }) { Text("Not now") }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeInboxRow(
    item: InboxItem,
    categories: List<com.kashvault.domain.Category>,
    sources: List<com.kashvault.domain.PaymentSource>,
    reason: String,
    alreadyBooked: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val palette = vaultPalette()
    val acceptAction by rememberUpdatedState(onAccept)
    val dismissAction by rememberUpdatedState(onDismiss)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    platformHapticTick()
                    acceptAction()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    platformHapticTick()
                    dismissAction()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val accept = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (accept) palette.income else palette.spend
            val icon = if (accept) Icons.Default.Check else Icons.Default.Close
            val align = if (accept) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(color.copy(alpha = 0.16f))
                    .padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (accept) "Accept" else "Dismiss", color = color, style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    ) {
        CompactInboxCard(
            item = item,
            categories = categories,
            sources = sources,
            reason = reason,
            alreadyBooked = alreadyBooked,
            onClick = onOpen,
        )
    }
}

private fun resolveInboxSourceLabel(
    item: InboxItem,
    sources: List<com.kashvault.domain.PaymentSource>,
): Pair<String, Boolean> {
    val source = sources.find { it.id == item.suggestedSourceId }
    val isCard = source?.kind == com.kashvault.domain.Constants.SOURCE_CREDIT_CARD ||
        isCardSpend(item.body) ||
        isCardBillPayment(item.body) ||
        extractInstrumentType(item.sender, item.body) == "CREDIT_CARD"

    val digits = source?.last4?.takeIf { it.isNotBlank() }
        ?: item.matchedSourceTag?.filter { it.isDigit() }?.takeIf { it.length in 3..6 }
        ?: extractAccountIdentifier(item.body)

    val sourceName = source?.name?.trim()?.takeIf { it.isNotBlank() }

    val label = when {
        isCard -> when {
            digits != null && sourceName != null -> {
                if (sourceName.contains(digits)) {
                    if (sourceName.contains("card", ignoreCase = true) || sourceName.contains("cc", ignoreCase = true)) {
                        sourceName
                    } else {
                        "CC · $sourceName"
                    }
                } else if (sourceName.contains("card", ignoreCase = true) || sourceName.contains("cc", ignoreCase = true)) {
                    "$sourceName · $digits"
                } else {
                    "$sourceName · CC $digits"
                }
            }
            digits != null -> "CC · $digits"
            sourceName != null -> {
                if (sourceName.contains("card", ignoreCase = true) || sourceName.contains("cc", ignoreCase = true)) {
                    sourceName
                } else {
                    "CC · $sourceName"
                }
            }
            else -> "Credit Card"
        }
        else -> when {
            digits != null && sourceName != null -> {
                if (sourceName.contains(digits)) {
                    sourceName
                } else {
                    "$sourceName · A/c $digits"
                }
            }
            digits != null -> "A/c · $digits"
            sourceName != null -> source.displayName()
            else -> "No account"
        }
    }
    return label to isCard
}

private fun resolveInboxTags(
    item: InboxItem,
    categories: List<com.kashvault.domain.Category>,
): Pair<String, String?> {
    val category = categories.find { it.id == item.suggestedCategoryId }
    val categoryName = category?.name ?: inboxCategoryBadge(item, categories)
    val rawTag = item.matchedCategoryTag?.trim()?.takeIf { it.isNotBlank() }
        ?: learnableKeywordToken(item)
    val distinctTag = rawTag?.takeIf { !it.equals(categoryName, ignoreCase = true) }
    return categoryName to distinctTag
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactInboxCard(
    item: InboxItem,
    categories: List<com.kashvault.domain.Category>,
    sources: List<com.kashvault.domain.PaymentSource>,
    reason: String,
    alreadyBooked: Boolean,
    onClick: () -> Unit,
) {
    val merchant = inboxMerchantHint(item.body, item.note, item.matchedCategoryTag, item.sender)
        ?: item.sender.ifBlank { "Unknown" }
    val (categoryName, distinctTag) = resolveInboxTags(item, categories)
    val (sourceLabel, isCard) = resolveInboxSourceLabel(item, sources)
    val category = categories.find { it.id == item.suggestedCategoryId }

    KashCard(onClick = onClick, contentPadding = 12.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MerchantMark(
                    name = merchant,
                    glyph = category?.icon,
                    colorHex = category?.color,
                    size = 40,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(merchant, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatTimestamp(item.capturedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reason.isNotBlank() || alreadyBooked) {
                        Text(
                            if (alreadyBooked) {
                                listOf("Already in History", reason).filter { it.isNotBlank() }.joinToString(" · ")
                            } else {
                                reason
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SoftChip(
                            categoryName,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (distinctTag != null) {
                            SoftChip(
                                "#${distinctTag.lowercase()}",
                                container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        SoftChip(
                            sourceLabel,
                            container = if (isCard) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            content = if (isCard) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                MoneyText(item.amount, type = item.type, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InboxAdjustSheet(
    item: InboxItem,
    categories: List<com.kashvault.domain.Category>,
    banks: List<com.kashvault.domain.PaymentSource>,
    cards: List<com.kashvault.domain.PaymentSource>,
    dueMatchTitle: String?,
    cardBillTitle: String?,
    preferredBankId: String?,
    onDismissSheet: () -> Unit,
    onAccept: (
        categoryId: String?,
        sourceId: String?,
        note: String?,
        timestamp: String,
        applyDue: Boolean,
        applyCardBill: Boolean,
        amount: Double?,
        type: String?,
    ) -> Unit,
    onDismissItem: () -> Unit,
) {
    var selectedType by remember(item.id) { mutableStateOf(item.type) }
    var amountText by remember(item.id) {
        mutableStateOf(if (item.amount % 1.0 == 0.0) item.amount.toLong().toString() else item.amount.toString())
    }
    val typedCategories = categories.filter { category ->
        val categoryType = category.type ?: "expense"
        categoryType == selectedType || (selectedType == "expense" && categoryType != "income")
    }.ifEmpty { categories }
    var categoryId by remember(item.id, selectedType) {
        mutableStateOf(
            preferredExpenseCategoryId(
                typedCategories,
                suggestedId = item.suggestedCategoryId,
                type = selectedType,
            ),
        )
    }
    var sourceId by remember(item.id, preferredBankId) {
        mutableStateOf(preferredBankId ?: item.suggestedSourceId)
    }
    var note by remember(item.id) { mutableStateOf(item.note.orEmpty()) }
    var date by remember(item.id) { mutableStateOf(parseLocalDateOrNull(item.capturedAt) ?: todayLocalDate()) }
    var applyDue by remember(item.id) { mutableStateOf(dueMatchTitle != null) }
    var applyCardBill by remember(item.id) { mutableStateOf(cardBillTitle != null) }
    var showFullMessage by remember(item.id) { mutableStateOf(false) }
    val selectedCategory = typedCategories.find { it.id == categoryId } ?: typedCategories.firstOrNull()
    val paySources = if (applyCardBill && cardBillTitle != null && banks.isNotEmpty()) banks else banks + cards
    val selectedSource = paySources.find { it.id == sourceId } ?: (banks + cards).find { it.id == sourceId }
    val clicks = rememberSingleClick()
    val palette = vaultPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.sender.ifBlank { "Payment" }, style = MaterialTheme.typography.titleLarge)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedType == "expense",
                    onClick = { selectedType = "expense" },
                    label = { Text("Expense (Debit)") },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.spendContainer,
                        selectedLabelColor = palette.spend,
                    ),
                )
                FilterChip(
                    selected = selectedType == "income",
                    onClick = { selectedType = "income" },
                    label = { Text("Income (Credit)") },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.incomeContainer,
                        selectedLabelColor = palette.income,
                    ),
                )
            }

            VaultOutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Verified Amount (${com.kashvault.domain.Constants.RUPEE_SIGN})") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                ),
                shape = RoundedCornerShape(16.dp),
            )

            val (sheetCategoryName, sheetDistinctTag) = resolveInboxTags(item, categories)
            val (sheetSourceLabel, sheetIsCard) = resolveInboxSourceLabel(item, banks + cards)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SoftChip(
                    sheetCategoryName,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (sheetDistinctTag != null) {
                    SoftChip(
                        "#${sheetDistinctTag.lowercase()}",
                        container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                SoftChip(
                    sheetSourceLabel,
                    container = if (sheetIsCard) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    content = if (sheetIsCard) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (item.body.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showFullMessage = !showFullMessage },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Original Message",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = if (showFullMessage) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                                Icon(
                                    imageVector = if (showFullMessage) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showFullMessage) "Hide full message" else "Show full message",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        AnimatedVisibility(visible = showFullMessage) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                SelectionContainer {
                                    Text(
                                        text = item.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            DatePickerField(label = "Date", date = date, onDate = { date = it })
            VaultOutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                shape = RoundedCornerShape(16.dp),
            )
            if (!(applyCardBill && cardBillTitle != null)) {
                SearchablePickerField(
                    label = "Category",
                    selectedLabel = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "Choose a category",
                    options = typedCategories.map { PickerOption(it.id, "${it.icon} ${it.name}", it.type) },
                    onSelect = { categoryId = it },
                    placeholder = "Search categories",
                )
            }
            if (paySources.isNotEmpty()) {
                SearchablePickerField(
                    label = if (applyCardBill && cardBillTitle != null) "Pay from" else "Account",
                    selectedLabel = selectedSource?.displayLabel() ?: "Bank or card",
                    options = paySources.map { PickerOption(it.id, it.displayLabel(), it.kindLabel()) },
                    onSelect = { sourceId = it },
                    placeholder = "Search banks and cards",
                )
            }
            if (cardBillTitle != null) {
                FilterChip(
                    selected = applyCardBill,
                    onClick = { applyCardBill = !applyCardBill },
                    label = { Text(cardBillTitle) },
                )
            } else if (dueMatchTitle != null) {
                FilterChip(
                    selected = applyDue,
                    onClick = { applyDue = !applyDue },
                    label = { Text("This is $dueMatchTitle") },
                )
            }
            val parsedAmount = com.kashvault.util.parseMoneyInput(amountText).takeIf { it.isFinite() && it > 0 } ?: item.amount
            PrimaryButton(
                text = "Accept ${formatINR(parsedAmount)}",
                onClick = {
                    clicks("accept") {
                        onAccept(
                            categoryId,
                            sourceId,
                            note.ifBlank { null },
                            timestampOnDate(date),
                            applyDue,
                            applyCardBill && cardBillTitle != null,
                            parsedAmount,
                            selectedType,
                        )
                    }
                },
            )
            SecondaryButton(text = "Dismiss", onClick = onDismissItem)
        }
    }
}
