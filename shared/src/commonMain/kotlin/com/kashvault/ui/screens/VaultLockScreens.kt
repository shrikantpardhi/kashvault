package com.kashvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.kashvault.lock.requestDeviceAuth
import com.kashvault.lock.isDeviceAuthAvailable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.SecondaryButton
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.navigation.KashVaultScaffold

@Composable
fun VaultLockGate() {
    val vm = LocalFinanceViewModel.current
    val state by vm.state.collectAsState()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val useDeviceLock = state.useDeviceLock
    val deviceAvailable = isDeviceAuthAvailable()
    val coroutineScope = rememberCoroutineScope()

    if (useDeviceLock && deviceAvailable) {
        LaunchedEffect(Unit) {
            val ok = requestDeviceAuth("Unlock KashVault")
            if (ok) vm.unlockWithDevice()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Branding & Security Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(com.kashvault.ui.theme.NeoCyan.copy(alpha = 0.25f), com.kashvault.ui.theme.NeoEmerald.copy(alpha = 0.15f)),
                            ),
                        )
                        .border(1.dp, com.kashvault.ui.theme.NeoCyan.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bank Grade Security",
                        tint = com.kashvault.ui.theme.NeoCyan,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "KashVault Institutional",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = com.kashvault.ui.theme.NeoEmerald.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.kashvault.ui.theme.NeoEmerald.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(com.kashvault.ui.theme.NeoEmerald),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "APP ACCESS LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = com.kashvault.ui.theme.NeoEmerald,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (useDeviceLock && deviceAvailable) "Touch biometric or enter PIN to authenticate" else "Enter your vault security PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Glowing PIN Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val maxDots = maxOf(4, pin.length)
                    for (i in 0 until maxDots) {
                        val isFilled = i < pin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) com.kashvault.ui.theme.NeoCyan else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) com.kashvault.ui.theme.NeoCyanGlow else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Neo-Fintech Numeric Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL"),
                )

                for (row in numRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (key in row) {
                            when (key) {
                                "BIO" -> {
                                    if (useDeviceLock && deviceAvailable) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(com.kashvault.ui.theme.NeoCyan.copy(alpha = 0.15f))
                                                .clickable {
                                                    com.kashvault.ui.haptics.platformHapticTick()
                                                    coroutineScope.launch {
                                                        val ok = requestDeviceAuth("Unlock KashVault")
                                                        if (ok) vm.unlockWithDevice()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometric Unlock",
                                                tint = com.kashvault.ui.theme.NeoCyan,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(64.dp))
                                    }
                                }
                                "DEL" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = pin.isNotEmpty()) {
                                                com.kashvault.ui.haptics.platformHapticTick()
                                                if (pin.isNotEmpty()) {
                                                    pin = pin.dropLast(1)
                                                    error = null
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = if (pin.isNotEmpty()) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                                            .clickable {
                                                com.kashvault.ui.haptics.platformHapticTick()
                                                if (pin.length < 8) {
                                                    val newP = pin + key
                                                    pin = newP
                                                    error = null
                                                    if (newP.length >= 4) {
                                                        if (vm.unlockVault(newP)) {
                                                            pin = ""
                                                        } else if (newP.length >= 6) {
                                                            error = "Incorrect PIN. Try again."
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (pin.length >= 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Unlock Vault",
                        onClick = {
                            if (vm.unlockVault(pin)) {
                                pin = ""
                            } else {
                                error = "PIN doesn't match"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
            }
        }
    }
}

object AppLockScreen : Screen {
    override val key = "app-lock"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        
        var showPinSheet by remember { mutableStateOf(false) }
        var pinSheetMode by remember { mutableStateOf("set") } // "set", "change", "reset", "disable"
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var currentPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        
        val enabled = state.vaultLockEnabled

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "App lock") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Device Lock Card
                item {
                    val deviceAvailable = isDeviceAuthAvailable()
                    KashCard {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                Text("Use Device Lock", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Recommended",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Use your phone's PIN or biometric.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = state.useDeviceLock,
                                onCheckedChange = { checked -> if (deviceAvailable) vm.setUseDeviceLock(checked) },
                                enabled = deviceAvailable,
                            )
                        }
                    }
                }
                
                // KashVault PIN Card
                item {
                    KashCard {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Header with toggle
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                    Text("Use KashVault PIN", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Optional",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Additional PIN for this app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = enabled,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            pinSheetMode = "set"
                                            newPin = ""
                                            confirmPin = ""
                                            currentPin = ""
                                            error = null
                                            showPinSheet = true
                                        } else {
                                            pinSheetMode = "disable"
                                            newPin = ""
                                            confirmPin = ""
                                            currentPin = ""
                                            error = null
                                            showPinSheet = true
                                        }
                                    },
                                )
                            }
                            
                            // Action buttons
                            if (enabled) {
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SecondaryButton(
                                            text = "Change PIN",
                                            onClick = {
                                                pinSheetMode = "change"
                                                currentPin = ""
                                                newPin = ""
                                                confirmPin = ""
                                                error = null
                                                showPinSheet = true
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        SecondaryButton(
                                            text = "Reset PIN",
                                            onClick = {
                                                pinSheetMode = "reset"
                                                currentPin = ""
                                                newPin = ""
                                                confirmPin = ""
                                                error = null
                                                showPinSheet = true
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    SecondaryButton(
                                        text = "Disable PIN",
                                        onClick = {
                                            pinSheetMode = "disable"
                                            currentPin = ""
                                            error = null
                                            showPinSheet = true
                                        },
                                    )
                                }
                            } else {
                                PrimaryButton(
                                    text = "Set PIN",
                                    onClick = {
                                        pinSheetMode = "set"
                                        newPin = ""
                                        confirmPin = ""
                                        currentPin = ""
                                        error = null
                                        showPinSheet = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // PIN Management Bottom Sheet
        if (showPinSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showPinSheet = false }
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Title
                    val title = when (pinSheetMode) {
                        "set" -> "Set PIN"
                        "change" -> "Change PIN"
                        "reset" -> "Reset PIN"
                        "disable" -> "Disable PIN"
                        else -> "PIN Settings"
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Current PIN field (for change/disable/reset)
                    if (pinSheetMode in listOf("change", "reset", "disable")) {
                        VaultOutlinedTextField(
                            value = currentPin,
                            onValueChange = { currentPin = it.filter { ch -> ch.isDigit() }.take(8) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            label = { Text("Current PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // New PIN field (not for disable)
                    if (pinSheetMode != "disable") {
                        VaultOutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it.filter { ch -> ch.isDigit() }.take(8) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            label = { Text("New PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        VaultOutlinedTextField(
                            value = confirmPin,
                            onValueChange = { confirmPin = it.filter { ch -> ch.isDigit() }.take(8) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = error != null,
                            supportingText = error?.let { { Text(it) } },
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // Error message for disable
                    if (pinSheetMode == "disable" && error != null) {
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Action buttons
                    PrimaryButton(
                        text = when (pinSheetMode) {
                            "set" -> "Set PIN"
                            "change" -> "Change PIN"
                            "reset" -> "Reset PIN"
                            "disable" -> "Disable PIN"
                            else -> "Confirm"
                        },
                        onClick = {
                            when (pinSheetMode) {
                                "set" -> {
                                    if (newPin.isEmpty() || confirmPin.isEmpty()) {
                                        error = "Enter a PIN"
                                    } else if (newPin != confirmPin) {
                                        error = "PINs don't match"
                                    } else if (newPin.length < 4) {
                                        error = "PIN must be 4-8 digits"
                                    } else {
                                        val ok = vm.setVaultPin(newPin, confirmPin)
                                        if (ok) {
                                            showPinSheet = false
                                        } else {
                                            error = "Failed to set PIN"
                                        }
                                    }
                                }
                                "change" -> {
                                    if (newPin.isEmpty() || confirmPin.isEmpty()) {
                                        error = "Enter a PIN"
                                    } else if (newPin != confirmPin) {
                                        error = "PINs don't match"
                                    } else if (newPin.length < 4) {
                                        error = "PIN must be 4-8 digits"
                                    } else {
                                        val ok = vm.changeVaultPin(currentPin, newPin, confirmPin)
                                        if (ok) {
                                            showPinSheet = false
                                        } else {
                                            error = "Incorrect current PIN"
                                        }
                                    }
                                }
                                "reset" -> {
                                    if (newPin.isEmpty() || confirmPin.isEmpty()) {
                                        error = "Enter a PIN"
                                    } else if (newPin != confirmPin) {
                                        error = "PINs don't match"
                                    } else if (newPin.length < 4) {
                                        error = "PIN must be 4-8 digits"
                                    } else {
                                        val ok = vm.changeVaultPin(currentPin, newPin, confirmPin)
                                        if (ok) {
                                            showPinSheet = false
                                        } else {
                                            error = "Incorrect current PIN"
                                        }
                                    }
                                }
                                "disable" -> {
                                    if (currentPin.isEmpty()) {
                                        error = "Enter your current PIN"
                                    } else {
                                        val ok = vm.disableVaultLock(currentPin)
                                        if (ok) {
                                            showPinSheet = false
                                        } else {
                                            error = "Incorrect PIN"
                                        }
                                    }
                                }
                            }
                        },
                        enabled = if (pinSheetMode == "disable") currentPin.isNotEmpty() else newPin.isNotEmpty() && confirmPin.isNotEmpty(),
                    )
                    
                    SecondaryButton(
                        text = "Cancel",
                        onClick = { showPinSheet = false },
                    )
                }
            }
        }
    }
}
