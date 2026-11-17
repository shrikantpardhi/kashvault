package com.kashvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.domain.Constants
import com.kashvault.ui.components.BrandMark
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.navigation.MainTabs
import com.kashvault.ui.navigation.TabsScreen
import com.kashvault.ui.rememberSingleClick
import com.kashvault.util.parseMoneyInput

object SetupScreen : Screen {
    override val key = "setup"

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val clicks = rememberSingleClick()

        // Account Type: Bank vs Credit Card vs Cash Wallet
        var selectedKind by remember { mutableStateOf(Constants.SOURCE_BANK) }

        // Form Fields
        var accountName by remember { mutableStateOf("") }
        var last4Digits by remember { mutableStateOf("") }
        var balanceOrOutstanding by remember { mutableStateOf("") }

        fun finish() {
            MainTabs.reset()
            navigator.replaceAll(TabsScreen)
        }

        val bankPresets = listOf("HDFC Bank", "State Bank of India", "ICICI Bank", "Axis Bank", "Kotak Mahindra", "Canara Bank")
        val cardPresets = listOf("HDFC Millennia", "ICICI Amazon Pay", "SBI SimplyCLICK", "Axis Flipkart", "Amex Platinum", "OneCard")
        val cashPresets = listOf("Cash in Hand", "Petty Cash Wallet", "Household Cash")

        val currentPresets = when (selectedKind) {
            Constants.SOURCE_CREDIT_CARD -> cardPresets
            "cash" -> cashPresets
            else -> bankPresets
        }

        val isCard = selectedKind == Constants.SOURCE_CREDIT_CARD
        val isCash = selectedKind == "cash"

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        BrandMark(size = 30, onBrand = false)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "KashVault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    TextButton(
                        onClick = {
                            clicks("skipSetup") {
                                vm.skipSetup()
                                finish()
                            }
                        },
                        modifier = Modifier.testTag("setup_skip_button"),
                    ) {
                        Text(
                            "Skip for now",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Main Scrollable Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 480.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Hero Icon
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .shadow(4.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = when {
                                        isCard -> Icons.Outlined.CreditCard
                                        isCash -> Icons.Outlined.Wallet
                                        else -> Icons.Outlined.AccountBalance
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Add Your Primary Account",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Set up your main bank account, credit card, or wallet to begin tracking your cashflow.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Account Type Selector Tabs
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    AccountTypeTab(
                                        title = "Bank",
                                        icon = Icons.Outlined.AccountBalance,
                                        isSelected = selectedKind == Constants.SOURCE_BANK,
                                        onClick = {
                                            selectedKind = Constants.SOURCE_BANK
                                            accountName = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AccountTypeTab(
                                        title = "Card",
                                        icon = Icons.Outlined.CreditCard,
                                        isSelected = selectedKind == Constants.SOURCE_CREDIT_CARD,
                                        onClick = {
                                            selectedKind = Constants.SOURCE_CREDIT_CARD
                                            accountName = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    AccountTypeTab(
                                        title = "Cash",
                                        icon = Icons.Outlined.Wallet,
                                        isSelected = selectedKind == "cash",
                                        onClick = {
                                            selectedKind = "cash"
                                            accountName = "Cash in Hand"
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Preset Chips
                            Text(
                                text = when {
                                    isCard -> "Popular Credit Cards"
                                    isCash -> "Cash Presets"
                                    else -> "Popular Banks"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                currentPresets.forEach { preset ->
                                    val isSelected = accountName.equals(preset, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                accountName = preset
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                            Text(
                                                text = preset,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Details Input Card
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(20.dp),
                                    ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Name Field
                                    VaultOutlinedTextField(
                                        value = accountName,
                                        onValueChange = { accountName = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("setup_account_name_input"),
                                        label = {
                                            Text(
                                                when {
                                                    isCard -> "Card Name"
                                                    isCash -> "Wallet Name"
                                                    else -> "Bank Account Name"
                                                },
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                when {
                                                    isCard -> "e.g. HDFC Regalia"
                                                    isCash -> "e.g. Cash in Hand"
                                                    else -> "e.g. HDFC Salary Account"
                                                },
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                when {
                                                    isCard -> Icons.Default.CreditCard
                                                    isCash -> Icons.Outlined.Wallet
                                                    else -> Icons.Default.AccountBalance
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                    )

                                    // Last 4 Digits (for bank or card)
                                    if (!isCash) {
                                        VaultOutlinedTextField(
                                            value = last4Digits,
                                            onValueChange = { if (it.length <= 4) last4Digits = it.filter { ch -> ch.isDigit() } },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("setup_last4_input"),
                                            label = { Text("Last 4 Digits (Optional)") },
                                            placeholder = { Text("e.g. 4092") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Numbers,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        )
                                    }

                                    // Balance or Outstanding
                                    VaultOutlinedTextField(
                                        value = balanceOrOutstanding,
                                        onValueChange = { balanceOrOutstanding = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("setup_balance_input"),
                                        label = {
                                            Text(
                                                if (isCard) "Current Outstanding / Dues (Optional)"
                                                else "Opening Balance (Optional)",
                                            )
                                        },
                                        placeholder = { Text("0.00") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CurrencyRupee,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )

                                    Text(
                                        text = "💡 Tip: You can add more accounts, cards, and budgets anytime from the Accounts tab.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Privacy badge
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Stored on this device. The SQLite database is not encrypted by KashVault; no net-banking passwords are required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Submit Button
                            Button(
                                onClick = {
                                    clicks("completeSetup") {
                                        val actualKind = if (isCash) Constants.SOURCE_BANK else selectedKind
                                        val finalName = accountName.trim().ifEmpty {
                                            when {
                                                isCard -> "Credit Card"
                                                isCash -> "Cash in Hand"
                                                else -> "Primary Bank"
                                            }
                                        }
                                        val balanceVal = parseMoneyInput(balanceOrOutstanding).let { if (it.isFinite() && it > 0) it else 0.0 }
                                        vm.completeAccountSetup(
                                            name = finalName,
                                            kind = actualKind,
                                            last4 = last4Digits.takeIf { it.isNotBlank() },
                                            balanceOrOutstanding = balanceVal,
                                        )
                                        finish()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("setup_complete_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "Get Started",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTypeTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "tab_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_content",
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
