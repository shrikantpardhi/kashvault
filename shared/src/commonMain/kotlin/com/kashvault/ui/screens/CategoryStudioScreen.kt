package com.kashvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.domain.CATEGORY_COLOR_OPTIONS
import com.kashvault.domain.CATEGORY_EMOJI_OPTIONS
import com.kashvault.domain.Category
import com.kashvault.domain.isBuiltinCategory
import com.kashvault.domain.isIncome
import com.kashvault.domain.isSpend
import com.kashvault.domain.keywordsFromText
import com.kashvault.domain.keywordsToText
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.SecondaryButton
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.theme.hexToColor
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.util.formatINR
import com.kashvault.util.isWithinMonth
import com.kashvault.util.todayLocalDate

object CategoryStudioScreen : Screen {
    override val key = "categories"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()

        var showEditor by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Category?>(null) }
        var pendingDelete by remember { mutableStateOf<Category?>(null) }
        var query by remember { mutableStateOf("") }

        var name by remember { mutableStateOf("") }
        var icon by remember { mutableStateOf("🏷️") }
        var color by remember { mutableStateOf("#BB8BFF") }
        var type by remember { mutableStateOf("expense") }
        var keywordsText by remember { mutableStateOf("") }

        if (isLoading) {
            LoadingScreen()
            return
        }

        fun resetDraft() {
            name = ""
            icon = "🏷️"
            color = "#BB8BFF"
            type = "expense"
            keywordsText = ""
        }

        fun startEdit(category: Category) {
            editing = category
            showEditor = true
            name = category.name
            icon = category.icon
            color = category.color
            type = category.type ?: "expense"
            keywordsText = keywordsToText(category.keywords)
        }

        val today = todayLocalDate()
        val visible = remember(query, state.categories) {
            val needle = query.trim()
            val pool = state.categories.sortedBy { it.name.lowercase() }
            if (needle.isEmpty()) pool
            else pool.filter {
                it.name.contains(needle, ignoreCase = true) ||
                    it.keywords.orEmpty().any { tag -> tag.contains(needle, ignoreCase = true) }
            }
        }

        KashVaultScaffold(currentScreen = this, title = "Categories") { modifier ->
            Column(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        VaultOutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    items(visible, key = { it.id }) { cat ->
                        val monthAmount = state.expenses
                            .filter { it.categoryId == cat.id && isWithinMonth(it.timestamp, today) }
                            .filter { if (cat.type == "income") it.isIncome() else it.isSpend() }
                            .sumOf { it.amount }
                        CategoryRow(
                            category = cat,
                            monthAmount = monthAmount,
                            onEdit = { startEdit(cat) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    PrimaryButton(
                        text = "Add category",
                        onClick = {
                            editing = null
                            resetDraft()
                            showEditor = true
                        },
                    )
                }
            }
        }

        if (showEditor) {
            val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showEditor = false },
                sheetState = editorSheetState,
            ) {
                CategoryEditor(
                    title = if (editing != null) "Edit category" else "New category",
                    name = name,
                    onName = { name = it },
                    icon = icon,
                    onIcon = { icon = it },
                    color = color,
                    onColor = { color = it },
                    type = type,
                    onType = { type = it },
                    showType = editing == null,
                    keywordsText = keywordsText,
                    onKeywords = { keywordsText = it },
                    onSave = {
                        if (name.isBlank()) return@CategoryEditor
                        val current = editing
                        if (current != null) {
                            vm.saveCategory(
                                current.copy(
                                    name = name.trim(),
                                    icon = icon,
                                    color = color,
                                    keywords = keywordsFromText(keywordsText),
                                ),
                            )
                        } else {
                            vm.addCategory(name.trim(), icon, color, type, keywordsFromText(keywordsText))
                        }
                        showEditor = false
                        editing = null
                        resetDraft()
                    },
                    onCancel = {
                        showEditor = false
                        editing = null
                        resetDraft()
                    },
                    onDelete = editing?.takeIf { !isBuiltinCategory(it.id) }?.let { cat ->
                        {
                            pendingDelete = cat
                            showEditor = false
                        }
                    },
                )
            }
        }

        pendingDelete?.let { cat ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete category?") },
                text = { Text("Existing transactions keep this category ID. Delete only if unused.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteCategory(cat.id)
                        pendingDelete = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    monthAmount: Double,
    onEdit: () -> Unit,
) {
    val palette = vaultPalette()
    val monthColor = if (category.type == "income") palette.income else palette.spend
    KashCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(hexToColor(category.color).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(category.icon, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                val keywords = category.keywords.orEmpty()
                Text(
                    if (keywords.isEmpty()) "No keywords" else keywords.take(4).joinToString(" · ") + if (keywords.size > 4) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (monthAmount > 0) {
                        "${if (category.type == "income") "This month" else "Spent this month"} ${formatINR(monthAmount)}"
                    } else {
                        "No activity this month"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (monthAmount > 0) monthColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }
    }
}

@Composable
private fun CategoryEditor(
    title: String,
    name: String,
    onName: (String) -> Unit,
    icon: String,
    onIcon: (String) -> Unit,
    color: String,
    onColor: (String) -> Unit,
    type: String,
    onType: (String) -> Unit,
    showType: Boolean,
    keywordsText: String,
    onKeywords: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var showEmojiSelector by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        VaultOutlinedTextField(name, onName, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (showType) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "expense", onClick = { onType("expense") }, label = { Text("Expense") })
                FilterChip(selected = type == "income", onClick = { onType("income") }, label = { Text("Income") })
            }
        }
        VaultOutlinedTextField(
            keywordsText,
            onKeywords,
            label = { Text("SMS tags") },
            placeholder = { Text("swiggy, uber, netflix") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    showEmojiSelector = !showEmojiSelector
                    showColorSelector = false
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Emoji  $icon")
            }
            OutlinedButton(
                onClick = {
                    showColorSelector = !showColorSelector
                    showEmojiSelector = false
                },
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(parseHex(color)),
                )
                Text("  Color")
            }
        }
        if (showEmojiSelector) {
            EmojiSelector(selected = icon) {
                onIcon(it)
                showEmojiSelector = false
            }
        }
        if (showColorSelector) {
            ColorSelector(selected = color) {
                onColor(it)
                showColorSelector = false
            }
        }
        PrimaryButton(text = "Save", onClick = onSave)
        SecondaryButton(text = "Cancel", onClick = onCancel)
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.height(48.dp)) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiSelector(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (CATEGORY_EMOJI_OPTIONS + selected).distinct().forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected == emoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else Color.Transparent,
                    )
                    .clickable { onSelect(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSelector(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (CATEGORY_COLOR_OPTIONS + selected).distinct().forEach { hex ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseHex(hex))
                    .border(
                        width = if (selected.equals(hex, ignoreCase = true)) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(hex) },
            )
        }
    }
}

fun parseHex(hex: String): Color = hexToColor(hex, Color.Gray)
