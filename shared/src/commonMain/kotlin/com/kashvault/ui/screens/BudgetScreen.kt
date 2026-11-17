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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.domain.Budget
import com.kashvault.domain.Category
import com.kashvault.domain.isSpend
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.FilterIconButton
import com.kashvault.ui.components.FilterRow
import com.kashvault.ui.components.FilterSection
import com.kashvault.ui.components.FilterSheet
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.ProgressBar
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.util.formatINR
import com.kashvault.util.getFilteredExpenses
import com.kashvault.util.parseMoneyInput

object BudgetScreen : Screen {
    override val key = "budget"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val isLoading by vm.isLoading.collectAsState()

        if (isLoading) {
            LoadingScreen()
            return
        }

        val state by vm.state.collectAsState()
        val budgetUsage = vm.getBudgetUsage()
        val expenseCats = remember(state.categories) { state.categories.filter { it.type != "income" } }
        val spentThisMonth = remember(state.expenses) {
            getFilteredExpenses(state.expenses, "monthly")
                .filter { it.isSpend() }
                .groupBy { it.categoryId }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
        }

        val limits = remember { mutableStateMapOf<String, String>() }
        androidx.compose.runtime.LaunchedEffect(state.budgets, state.categories) {
            limits.clear()
            state.budgets.forEach { b ->
                val limitStr = if (b.limit % 1.0 == 0.0) b.limit.toLong().toString() else b.limit.toString()
                limits[b.categoryId] = limitStr
            }
            expenseCats.forEach { cat ->
                if (!limits.containsKey(cat.id)) limits[cat.id] = ""
            }
        }

        var query by remember { mutableStateOf("") }
        var showAll by remember { mutableStateOf(false) }
        var showFilters by remember { mutableStateOf(false) }

        val needle = query.trim()
        val visible = expenseCats.filter { category ->
            val matchesQuery = needle.isEmpty() ||
                category.name.contains(needle, ignoreCase = true)
            if (!matchesQuery) return@filter false
            if (showAll || needle.isNotEmpty()) return@filter true
            val parsedLimit = parseMoneyInput(limits[category.id] ?: "")
            val hasLimit = parsedLimit.isFinite() && parsedLimit > 0
            val spent = spentThisMonth[category.id] ?: 0.0
            hasLimit || spent > 0
        }

        KashVaultScaffold(currentScreen = this, title = "Budget") { modifier ->
            Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
                KashCard(modifier = Modifier.height(80.dp)) {
                    val used = if (budgetUsage.totalLimit > 0) {
                        "${budgetUsage.overallPercent.toInt()}% used · ${formatINR(budgetUsage.totalSpent)} of ${formatINR(budgetUsage.totalLimit)}"
                    } else {
                        "Set a limit on the categories you care about."
                    }
                    Text(used, style = MaterialTheme.typography.bodyMedium)
                    if (budgetUsage.totalLimit > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ProgressBar(budgetUsage.overallPercent)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                VaultOutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Find a category") },
                    singleLine = true,
                    trailingIcon = {
                        FilterIconButton(
                            activeCount = if (showAll) 1 else 0,
                            onClick = { showFilters = true },
                        )
                    },
                )
                Text(
                    if (showAll || needle.isNotEmpty()) {
                        "${visible.size} categories"
                    } else {
                        "Used this month"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                if (needle.isNotEmpty()) {
                                    "No category matches that search."
                                } else {
                                    "Nothing spent this month yet. Use filters to show all and set limits."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(visible, key = { it.id }) { category ->
                        CompactBudgetRow(
                            category = category,
                            spent = spentThisMonth[category.id] ?: 0.0,
                            limitText = limits[category.id] ?: "",
                            onLimit = { limits[category.id] = it },
                        )
                    }
                }

                Button(
                    onClick = {
                        val budgets = limits.mapNotNull { (catId, limitStr) ->
                            val limit = parseMoneyInput(limitStr)
                            if (limit.isFinite() && limit > 0) Budget(catId, limit) else null
                        }
                        vm.saveBudgets(budgets)
                    },
                    modifier = Modifier.fillMaxWidth().height(com.kashvault.ui.theme.ButtonHeight),
                ) {
                    Text("Save budgets")
                }
            }
        }

        FilterSheet(
            visible = showFilters,
            onDismiss = { showFilters = false },
            onReset = { showAll = false },
        ) {
            FilterSection("Categories") {
                FilterRow(
                    options = listOf("used" to "Used this month", "all" to "Show all"),
                    selected = if (showAll) "all" else "used",
                    onSelect = { showAll = it == "all" },
                )
            }
        }
    }
}

@Composable
private fun CompactBudgetRow(
    category: Category,
    spent: Double,
    limitText: String,
    onLimit: (String) -> Unit,
) {
    val limit = parseMoneyInput(limitText)
    val hasLimit = limit.isFinite() && limit > 0
    KashCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${category.icon} ${category.name}")
                Text(
                    if (hasLimit) {
                        "${formatINR(spent)} of ${formatINR(limit)}"
                    } else if (spent > 0) {
                        "${formatINR(spent)} this month"
                    } else {
                        "No limit"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasLimit) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ProgressBar((spent / limit) * 100)
                }
            }
            VaultOutlinedTextField(
                value = limitText,
                onValueChange = onLimit,
                modifier = Modifier.width(112.dp),
                label = { Text("Limit") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
