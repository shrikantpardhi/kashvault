package com.kashvault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.kashvault.lock.isDeviceAuthAvailable
import com.kashvault.lock.requestDeviceAuth
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.navigation.KashVaultScaffold

@Composable
fun VaultLockGate() {
    val vm = LocalFinanceViewModel.current
    var attempt by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Authenticate to open KashVault.") }

    LaunchedEffect(attempt) {
        if (!isDeviceAuthAvailable()) {
            message = "Biometrics are unavailable. Enroll a biometric on this device to continue."
            return@LaunchedEffect
        }
        val authenticated = requestDeviceAuth("Unlock KashVault")
        if (authenticated) {
            vm.unlockWithDevice()
        } else {
            message = "Authentication was not completed. Try again to continue."
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "KashVault is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = { attempt += 1 },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Try biometrics again")
            }
        }
    }
}

object AppLockScreen : Screen {
    override val key = "app-lock"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val biometricAvailable = isDeviceAuthAvailable()

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "App lock") { modifier ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(PaddingValues(20.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KashCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Biometric app lock", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Authenticate with an enrolled Android biometric each time you launch KashVault. " +
                                "Device PIN, password, and KashVault PIN are not accepted as fallbacks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (state.vaultLockEnabled) "Enabled" else "Off",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Switch(
                                checked = state.vaultLockEnabled,
                                onCheckedChange = vm::setBiometricLockEnabled,
                                enabled = state.vaultLockEnabled || biometricAvailable,
                            )
                        }
                        if (!biometricAvailable) {
                            Text(
                                "Enroll a biometric in Android settings before enabling app lock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
