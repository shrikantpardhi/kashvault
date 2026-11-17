package com.kashvault.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.kashvault.auth.createGoogleAuth
import com.kashvault.data.FinanceRepository
import com.kashvault.db.DatabaseDriverFactory
import com.kashvault.domain.FinanceSnapshot
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.navigation.AppNavigatorHost
import com.kashvault.ui.navigation.MainTabs
import com.kashvault.ui.navigation.TabsScreen
import com.kashvault.ui.screens.*
import com.kashvault.ui.theme.KashVaultTheme
import com.kashvault.ui.theme.isDarkThemeEnabled
import com.kashvault.ui.theme.usesMaterialYou
import com.kashvault.util.needsFirstRunSetup
import com.kashvault.viewmodel.FinanceViewModel
import com.kashvault.reminder.InboxOpenRequest

@Composable
fun KashVaultApp(driverFactory: DatabaseDriverFactory) {
    val repository = remember { FinanceRepository(driverFactory) }
    val googleAuth = remember { createGoogleAuth() }
    val viewModel = remember { FinanceViewModel(repository, googleAuth) }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val darkTheme = isDarkThemeEnabled(themePreference, isSystemInDarkTheme())
    val materialYou = usesMaterialYou(themePreference)
    val latestToast by viewModel.latestToast.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lockEnabled by viewModel.state.collectAsState()
    val unlocked by viewModel.vaultUnlocked.collectAsState()
    val showLock = !isLoading && lockEnabled.vaultLockEnabled && !unlocked

    LaunchedEffect(latestToast?.id) {
        snackbarHostState.currentSnackbarData?.dismiss()
        val current = latestToast ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = current.message,
            actionLabel = current.actionLabel,
            withDismissAction = true,
            duration = if (current.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.performToastAction()
        }
        viewModel.consumeToast(current)
    }

    KashVaultTheme(darkTheme = darkTheme, materialYou = materialYou) {
        CompositionLocalProvider(LocalFinanceViewModel provides viewModel) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    LoadingScreen()
                } else if (showLock) {
                    VaultLockGate()
                } else {
                    val startScreen = remember {
                        startScreenForSnapshot(viewModel.state.value)
                    }
                    Navigator(startScreen) { navigator ->
                        LaunchedEffect(viewModel) {
                            viewModel.signedOut.collect {
                                MainTabs.reset()
                                navigator.replaceAll(LoginScreen)
                            }
                        }
                        val openInbox by InboxOpenRequest.pending.collectAsState()
                        LaunchedEffect(openInbox) {
                            if (!openInbox) return@LaunchedEffect
                            val current = navigator.lastItem
                            if (current !is LoginScreen && current.key != InboxScreen.key) {
                                navigator.push(InboxScreen)
                            }
                            InboxOpenRequest.consume()
                        }
                        AppNavigatorHost(navigator)
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 88.dp),
                )
            }
        }
    }
}

internal fun startScreenForSnapshot(snapshot: FinanceSnapshot): Screen = when {
    snapshot.authChoice == null && snapshot.googleSession == null -> LoginScreen
    needsFirstRunSetup(snapshot.setupCompleted, snapshot.paymentSources.size, snapshot.expenses.size) -> SetupScreen
    else -> TabsScreen
}
