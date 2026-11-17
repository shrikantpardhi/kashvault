package com.kashvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.kashvault.ai.GEMINI_MODEL_OPTIONS
import com.kashvault.ai.geminiModelLabel
import com.kashvault.ai.normalizeGeminiModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.ui.rememberSingleClick
import com.kashvault.backup.fileExists
import com.kashvault.backup.readFileBytes
import com.kashvault.domain.Constants
import com.kashvault.domain.UserProfile
import com.kashvault.domain.keywordsToText
import com.kashvault.inbox.DEFAULT_SMS_SCAN_KEYWORDS
import com.kashvault.ui.components.VaultOutlinedTextField
import com.kashvault.ui.components.KashCard
import com.kashvault.ui.components.LoadingScreen
import com.kashvault.ui.components.PersonAvatar
import com.kashvault.ui.components.PrimaryButton
import com.kashvault.ui.components.SecondaryButton
import com.kashvault.ui.components.SettingsGroup
import com.kashvault.ui.components.SettingsRow
import com.kashvault.ui.components.TimePickerField
import com.kashvault.ui.navigation.KashVaultScaffold
import com.kashvault.ui.navigation.MainTabs
import com.kashvault.ui.theme.KashVaultTheme
import com.kashvault.ui.theme.THEME_DARK
import com.kashvault.ui.theme.THEME_DYNAMIC
import com.kashvault.ui.theme.THEME_LIGHT
import com.kashvault.ui.theme.THEME_SYSTEM
import com.kashvault.ui.theme.ScreenContentPadding
import com.kashvault.ui.theme.ScreenGap
import com.kashvault.ui.theme.isDarkThemeEnabled
import com.kashvault.ui.theme.normalizeTheme
import com.kashvault.ui.theme.themeLabel
import com.kashvault.ui.theme.vaultPalette
import com.kashvault.ui.toImageBitmapOrNull

object ProfileScreen : Screen {
    override val key = "profile"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val photoEpoch by vm.profilePhotoEpoch.collectAsState()
        val session = state.googleSession
        val displayName = state.userProfile?.name?.ifBlank { null } ?: session?.displayName ?: "Your profile"
        val displayEmail = state.userProfile?.email?.ifBlank { null } ?: session?.email ?: "Local vault"
        val photo = rememberProfilePhoto(state.userProfile?.imagePath, photoEpoch)
        val reminder = state.inboxReminder
        val pendingInbox = state.inboxItems.size
        val theme = normalizeTheme(state.userProfile?.theme)
        var showAuditDialog by remember { mutableStateOf(false) }
        val fingerprints = remember(state.expenses) { com.kashvault.util.TransactionFingerprints.fingerprintLedger(state.expenses) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "Settings") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = ScreenContentPadding,
                verticalArrangement = Arrangement.spacedBy(ScreenGap),
            ) {
                item {
                    KashCard(onClick = { navigator.push(PersonalDetailsScreen) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonAvatar(displayName, size = 56, image = photo)
                            Spacer(modifier = Modifier.size(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    displayEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    SettingsGroup(title = "Accounts") {
                        SettingsRow(
                            title = "Categories",
                            subtitle = "${state.categories.size} categories · emoji, keywords, spend",
                            onClick = { navigator.push(CategoryStudioScreen) },
                            icon = Icons.Default.Category,
                        )
                        SettingsRow(
                            title = "Banks & cards",
                            subtitle = if (state.paymentSources.isEmpty()) "Add a bank to start" else "${state.paymentSources.size} accounts",
                            onClick = { navigator.push(PaymentSourcesScreen) },
                            icon = Icons.Default.AccountBalance,
                        )
                        SettingsRow(
                            title = "Budget",
                            subtitle = "Monthly limits by category",
                            onClick = { navigator.push(BudgetScreen) },
                            icon = Icons.Default.PieChart,
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "Inbox") {
                        SettingsRow(
                            title = "Inbox",
                            subtitle = if (pendingInbox > 0) {
                                "$pendingInbox waiting to review"
                            } else {
                                "SMS and UPI payments"
                            },
                            onClick = { navigator.push(InboxScreen) },
                            icon = Icons.Default.Notifications,
                        )
                        SettingsRow(
                            title = "Message scan",
                            subtitle = messageScanSubtitle(reminder.enabled, reminder.hour, reminder.minute),
                            onClick = { navigator.push(ScanKeywordsScreen) },
                            icon = Icons.Default.Sms,
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "Preferences") {
                        ThemeDropdownRow(
                            theme = theme,
                            onThemeSelected = { vm.setTheme(it) },
                        )
                        SettingsRow(
                            title = "App lock",
                            subtitle = if (state.vaultLockEnabled) "PIN on" else "Off",
                            onClick = { navigator.push(AppLockScreen) },
                            icon = Icons.Default.Lock,
                        )
                        SettingsRow(
                            title = "Backup & restore",
                            subtitle = backupSubtitle(state.syncSettings.lastBackupAt, state.syncSettings.lastRestoreAt),
                            onClick = { navigator.push(BackupScreen) },
                            icon = Icons.Default.Backup,
                        )
                        SettingsRow(
                            title = "AI settings",
                            subtitle = if (state.userProfile?.geminiApiKey.isNullOrBlank()) {
                                "Set key · ${geminiModelLabel(state.geminiModel)}"
                            } else {
                                "${geminiModelLabel(state.geminiModel)} · Active"
                            },
                            onClick = { navigator.push(AiSettingsScreen) },
                            icon = Icons.Default.Psychology,
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "About & Legal") {
                        SettingsRow(
                            title = "Transaction Fingerprints",
                            subtitle = "${fingerprints.totalTransactions} informational SHA-256 fingerprints",
                            onClick = { showAuditDialog = true },
                            icon = Icons.Default.Shield,
                        )
                        SettingsRow(
                            title = "Privacy Policy & Safety",
                            subtitle = "Local storage, optional Google services, and data handling",
                            onClick = { navigator.push(PrivacyPolicyScreen) },
                            icon = Icons.Default.Shield,
                            showDivider = false,
                        )
                    }
                }
            }

            if (showAuditDialog) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                var copied by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showAuditDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF10B981),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transaction Fingerprints", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                fingerprints.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "COMBINED SHA-256 DIGEST",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFF06B6D4),
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = fingerprints.combinedDigest,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                                    modifier = Modifier.padding(8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("• ${fingerprints.totalTransactions} transaction fingerprints included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• The digest does not detect edits or verify authenticity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(fingerprints.combinedDigest))
                                copied = true
                            }) {
                                Text(if (copied) "Copied!" else "Copy Digest")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            PrimaryButton(text = "Done", onClick = { showAuditDialog = false })
                        }
                    },
                )
            }
        }
    }
}

object PersonalDetailsScreen : Screen {
    override val key = "personal-details"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val isSigningIn by vm.isSigningIn.collectAsState()
        val authError by vm.authError.collectAsState()
        val photoEpoch by vm.profilePhotoEpoch.collectAsState()

        var name by remember(state.userProfile) { mutableStateOf(state.userProfile?.name ?: "") }
        var email by remember(state.userProfile) { mutableStateOf(state.userProfile?.email ?: "") }
        var mobile by remember(state.userProfile) { mutableStateOf(state.userProfile?.mobile ?: "") }
        val photo = rememberProfilePhoto(state.userProfile?.imagePath, photoEpoch)
        val displayName = name.ifBlank { state.googleSession?.displayName ?: "You" }
        val clicks = rememberSingleClick()

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "Profile") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    KashCard {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clickable { vm.pickProfileImage() },
                            ) {
                                PersonAvatar(displayName, size = 96, image = photo)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = "Add profile photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { vm.pickProfileImage() }) {
                                Text(if (photo == null) "Add photo" else "Change photo")
                            }
                            if (state.userProfile?.imagePath != null) {
                                TextButton(onClick = { vm.clearProfileImage() }) { Text("Remove photo") }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        VaultOutlinedTextField(
                            name,
                            { name = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        VaultOutlinedTextField(
                            email,
                            { email = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        VaultOutlinedTextField(
                            mobile,
                            { mobile = it },
                            label = { Text("Mobile") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Save details",
                            onClick = {
                                vm.saveProfile(
                                    settingsProfile(
                                        existing = state.userProfile,
                                        name = name,
                                        email = email,
                                        mobile = mobile,
                                    ),
                                )
                            },
                        )
                    }
                }

                item {
                    GoogleAccountCard(
                        isSigningIn = isSigningIn,
                        authError = authError,
                        onSignIn = { vm.signInWithGoogle() },
                        onConfirmSignOut = { backupFirst -> vm.signOut(backupFirst) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleAccountCard(
    isSigningIn: Boolean,
    authError: String?,
    onSignIn: () -> Unit,
    onConfirmSignOut: (backupFirst: Boolean) -> Unit,
) {
    val vm = LocalFinanceViewModel.current
    val state by vm.state.collectAsState()
    val signingOut by vm.isSigningOut.collectAsState()
    val session = state.googleSession
    val canSignIn = vm.resolvedGoogleClientId().isNotBlank()
    var confirmSignOut by remember { mutableStateOf(false) }
    val clicks = rememberSingleClick()
    KashCard {
        if (session != null) {
            Text("Google", style = MaterialTheme.typography.titleMedium)
            Text(session.displayName ?: session.email ?: session.userId)
            session.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(text = "Sign out", onClick = { confirmSignOut = true })
        } else {
            Text("Google", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign in for identity and private Drive backups. Data stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (authError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(authError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (!canSignIn) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Google Sign-In is not built into this install. Continue without Google, or rebuild with GOOGLE_WEB_CLIENT_ID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = if (isSigningIn) "Signing in…" else "Sign in with Google",
                onClick = onSignIn,
                enabled = !isSigningIn && canSignIn,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton(text = "Sign out", onClick = { confirmSignOut = true })
        }
    }
    val restoreOffer by vm.driveRestoreOffer.collectAsState()
    if (confirmSignOut) {
        SignOutBackupDialog(
            googleSignedIn = session != null,
            onBackupAndSignOut = {
                clicks("signOutBackup") {
                    confirmSignOut = false
                    onConfirmSignOut(true)
                }
            },
            onSignOutWithoutBackup = {
                clicks("signOutSkipBackup") {
                    confirmSignOut = false
                    onConfirmSignOut(false)
                }
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (restoreOffer != null) {
        val backup = restoreOffer!!
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            title = {
                Text(
                    "Restore Drive Backup?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "An unencrypted SQLite backup was found in your Google Drive account" +
                            (backup.modifiedTime?.let { " from $it" } ?: "") +
                            ".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Would you like to restore your categories, accounts, and history, or keep current vault data?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { clicks("restore") { vm.acceptDriveRestore() } },
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Restore Backup")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { clicks("skipRestore") { vm.skipDriveRestore() } },
                    enabled = !isSigningIn,
                ) {
                    Text("Keep Current")
                }
            },
        )
    }
}

@Composable
private fun SignOutBackupDialog(
    googleSignedIn: Boolean,
    onBackupAndSignOut: () -> Unit,
    onSignOutWithoutBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                "Sign out & Erase Local Data?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Signing out will erase all your local financial records and accounts from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    if (googleSignedIn) {
                        "Back up to your private Google Drive now so you can restore your data whenever you sign back in."
                    } else {
                        "Save a backup file first to prevent losing your financial data."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onBackupAndSignOut,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (googleSignedIn) "Back up & sign out" else "Save file & sign out")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onSignOutWithoutBackup,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Sign out & erase")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

object BackupScreen : Screen {
    override val key = "backup"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val session = state.googleSession
        val lastBackup = state.syncSettings.lastBackupAt
        val lastRestore = state.syncSettings.lastRestoreAt
        val backups by vm.cloudBackups.collectAsState()
        val backingUp by vm.isBackingUp.collectAsState()
        val backupMessage by vm.backupMessage.collectAsState()
        val backupError by vm.backupError.collectAsState()
        var confirmRestore by remember { mutableStateOf(false) }
        var confirmLocalRestore by remember { mutableStateOf(false) }
        var showRestoreChooser by remember { mutableStateOf(false) }
        var eraseChoice by remember { mutableStateOf<Boolean?>(null) }
        val clicks = rememberSingleClick()

        if (isLoading) {
            LoadingScreen()
            return
        }

        LaunchedEffect(session?.accessToken) {
            if (session != null) vm.refreshCloudBackups()
        }

                        val status = backupError ?: backupMessage ?: if (session != null) {
                            "Sync: Google Drive"
                        } else {
                            "Sync: this device only"
                        }

        KashVaultScaffold(currentScreen = this, title = "Backup") { modifier ->
            Column(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        KashCard {
                            Text("Last backup", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                lastBackup?.let { formatQuietTimestamp(it) } ?: "Not yet",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            if (lastRestore != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Last restore ${formatQuietTimestamp(lastRestore)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (backupError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (session != null && backups.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Latest copy is on Google Drive",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    item {
                        KashCard {
                            Text("This device", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Export a .db file or replace this vault from one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryButton(
                        text = if (backingUp) "Working…" else "Backup now",
                        onClick = {
                            if (session != null) vm.backupToDrive() else vm.exportDatabaseFile()
                        },
                        enabled = !backingUp,
                    )
                    SecondaryButton(
                        text = "Restore",
                        onClick = { showRestoreChooser = true },
                        enabled = !backingUp,
                    )
                    SecondaryButton(
                        text = "Export file",
                        onClick = { vm.exportDatabaseFile() },
                        enabled = !backingUp,
                    )
                    Button(
                        onClick = { eraseChoice = false },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !backingUp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Erase local data")
                    }
                    Button(
                        onClick = { eraseChoice = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !backingUp && session != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Erase local + Google Drive backups")
                    }
                    if (session == null) {
                        Text(
                            "Sign in with Google to erase Drive backups. Local data can still be erased separately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        eraseChoice?.let { eraseDrive ->
            AlertDialog(
                onDismissRequest = { eraseChoice = null },
                title = { Text("Permanently erase data?") },
                text = {
                    Text(
                        if (eraseDrive) {
                            "This permanently deletes the local vault and app-managed local database backups, plus KashVault database backups in your private Google Drive app folder. Exported files saved elsewhere are not deleted. This cannot be undone."
                        } else {
                            "This permanently deletes the local vault and app-managed local database backups on this device. Google Drive backups are kept. Exported files saved elsewhere are not deleted. This cannot be undone."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val eraseDriveBackups = eraseDrive
                            eraseChoice = null
                            clicks(if (eraseDriveBackups) "eraseLocalAndDrive" else "eraseLocal") {
                                vm.eraseLocalData(eraseDriveBackups)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Erase permanently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { eraseChoice = null }) { Text("Cancel") }
                },
            )
        }

        if (confirmLocalRestore) {
            AlertDialog(
                onDismissRequest = { confirmLocalRestore = false },
                title = { Text("Replace this vault?") },
                text = { Text("The selected file will replace data on this device. You stay signed in.") },
                confirmButton = {
                    TextButton(onClick = {
                        clicks("import") {
                            confirmLocalRestore = false
                            vm.importDatabaseFile()
                        }
                    }) { Text("Choose file") }
                },
                dismissButton = { TextButton(onClick = { confirmLocalRestore = false }) { Text("Cancel") } },
            )
        }
        if (showRestoreChooser) {
            val driveAvailable = session != null && backups.isNotEmpty()
            AlertDialog(
                onDismissRequest = { showRestoreChooser = false },
                title = { Text("Restore from") },
                text = { Text(if (driveAvailable) "Choose a source to restore from." else "No Drive backup available; choose a local file to restore.") },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreChooser = false
                        if (driveAvailable) confirmRestore = true else confirmLocalRestore = true
                    }, enabled = driveAvailable) { Text("From Drive") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRestoreChooser = false
                        confirmLocalRestore = true
                    }) { Text("From file") }
                },
            )
        }
        if (confirmRestore) {
            AlertDialog(
                onDismissRequest = { confirmRestore = false },
                title = { Text("Replace this vault?") },
                text = { Text("Restores the latest Drive copy onto this device. You stay signed in.") },
                confirmButton = {
                    TextButton(onClick = {
                        clicks("restoreDrive") {
                            confirmRestore = false
                            vm.restoreLatestFromDrive()
                        }
                    }) { Text("Restore") }
                },
                dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
            )
        }
    }
}

object AiSettingsScreen : Screen {
    override val key = "ai-settings"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        var geminiKey by remember(state.userProfile) { mutableStateOf(state.userProfile?.geminiApiKey ?: "") }
        var selectedModel by remember(state.geminiModel) { mutableStateOf(normalizeGeminiModel(state.geminiModel)) }
        var showGeminiKey by remember { mutableStateOf(false) }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "AI settings") { modifier ->
            Column(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(ScreenGap),
                ) {
                    item {
                        Text(
                            "Configure a Google Gemini API key and choose the model for Magic Log, the AI Advisor, and cash-flow forecasts. Each request asks before sending the fields described in the privacy policy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        KashCard {
                            Text("AI Model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Selected model will be used throughout the application for all AI operations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            GEMINI_MODEL_OPTIONS.forEach { option ->
                                val isSelected = selectedModel == option.id
                                val subtitle = when (option.id) {
                                    "gemini-3.5-flash" -> "Fastest & recommended for everyday finance logs and advice"
                                    "gemini-2.5-flash" -> "Lightweight and efficient response generation"
                                    "gemini-2.5-pro" -> "Advanced reasoning for complex financial queries"
                                    "gemini-3.1-flash-lite" -> "Ultra-fast lightweight model"
                                    else -> option.id
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedModel = option.id
                                            vm.setGeminiModel(option.id)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    option.label,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                )
                                                if (option.id == "gemini-3.5-flash") {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                    ) {
                                                        Text(
                                                            "Default",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        KashCard {
                            Text("Gemini API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))
                            VaultOutlinedTextField(
                                value = geminiKey,
                                onValueChange = { geminiKey = it },
                                label = { Text("API key") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                        Icon(
                                            imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showGeminiKey) "Hide Gemini key" else "Show Gemini key",
                                        )
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "The API key is saved in this device's unencrypted SQLite database. It is sent directly to Google only when you confirm a Gemini request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    PrimaryButton(
                        text = "Save",
                        onClick = {
                            vm.setGeminiModel(selectedModel)
                            vm.saveProfile(
                                settingsProfile(
                                    existing = state.userProfile,
                                    geminiKey = geminiKey,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

object ScanKeywordsScreen : Screen {
    override val key = "scan-keywords"

    @Composable
    override fun Content() {
        val vm = LocalFinanceViewModel.current
        val state by vm.state.collectAsState()
        val isLoading by vm.isLoading.collectAsState()
        val reminder = state.inboxReminder
        val canNotify by vm.canPostNotifications.collectAsState()
        var keywordsText by remember(state.smsScanKeywords) {
            mutableStateOf(keywordsToText(state.smsScanKeywords.ifEmpty { DEFAULT_SMS_SCAN_KEYWORDS }))
        }

        LaunchedEffect(Unit) {
            vm.refreshInboxCapturePermissions()
        }

        if (isLoading) {
            LoadingScreen()
            return
        }

        KashVaultScaffold(currentScreen = this, title = "Message scan") { modifier ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        "SMS and UPI alerts are kept only if the message contains one of these words. Parsing stays on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    KashCard {
                        Text("Keywords", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        VaultOutlinedTextField(
                            value = keywordsText,
                            onValueChange = { keywordsText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Contains") },
                            placeholder = { Text("debited, UPI, HDFC") },
                            supportingText = {
                                Text("Comma-separated. Empty keeps every message that looks like a payment.")
                            },
                            minLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            SecondaryButton(
                                text = "Defaults",
                                onClick = { keywordsText = keywordsToText(DEFAULT_SMS_SCAN_KEYWORDS) },
                                modifier = Modifier.weight(1f),
                            )
                            PrimaryButton(
                                text = "Save",
                                onClick = { vm.saveSmsScanKeywords(keywordsText) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    KashCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily reminder", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "We’ll nudge you if scanned SMS or UPI payments are waiting.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = reminder.enabled,
                                onCheckedChange = { vm.setInboxReminderEnabled(it) },
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TimePickerField(
                            label = "Time",
                            hour = reminder.hour,
                            minute = reminder.minute,
                            onTime = { hour, minute -> vm.setInboxReminderTime(hour, minute) },
                            enabled = reminder.enabled,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "A short reminder once a day. Turn it off anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (reminder.enabled && !canNotify) {
                            TextButton(onClick = { vm.requestInboxReminderPermission() }, modifier = Modifier.height(48.dp)) {
                                Text("Allow notifications")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun backupSubtitle(lastBackup: String?, lastRestore: String?): String {
    val backup = lastBackup?.let { "Last backup ${formatQuietTimestamp(it)}" }
    val restore = lastRestore?.let { "Last restore ${formatQuietTimestamp(it)}" }
    return listOfNotNull(backup, restore).joinToString(" · ").ifBlank { "Drive or a local file" }
}

private fun messageScanSubtitle(enabled: Boolean, hour: Int, minute: Int): String {
    val reminder = if (enabled) {
        "Daily reminder ${com.kashvault.reminder.formatReminderClock(hour, minute)}"
    } else {
        "Daily reminder off"
    }
    return "Keywords · $reminder"
}

private fun formatQuietTimestamp(value: String): String = value.trim().take(10)

@Composable
internal fun rememberProfilePhoto(imagePath: String?, epoch: Long): ImageBitmap? =
    remember(imagePath, epoch) {
        val path = imagePath?.takeIf { fileExists(it) } ?: return@remember null
        runCatching { readFileBytes(path).toImageBitmapOrNull() }.getOrNull()
    }

@Composable
private fun ThemeDropdownRow(
    theme: String?,
    onThemeSelected: (String) -> Unit,
    showDivider: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentTheme = normalizeTheme(theme)
    val themeOptions = listOf(THEME_LIGHT, THEME_DARK, THEME_SYSTEM, THEME_DYNAMIC)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Theme", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "App appearance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                Surface(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            themeLabel(currentTheme),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Select theme",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    themeOptions.forEach { option ->
                        val isSelected = option == currentTheme
                        DropdownMenuItem(
                            text = {
                                Text(
                                    themeLabel(option),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                onThemeSelected(option)
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (option) {
                                        THEME_LIGHT -> Icons.Default.LightMode
                                        THEME_DARK -> Icons.Default.DarkMode
                                        THEME_DYNAMIC -> Icons.Default.ColorLens
                                        else -> Icons.Default.SettingsBrightness
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 62.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

private fun settingsProfile(
    existing: UserProfile?,
    name: String? = null,
    email: String? = null,
    mobile: String? = null,
    geminiKey: String? = null,
    theme: String? = null,
) = UserProfile(
    id = existing?.id ?: Constants.SINGLETON_ID,
    name = name ?: existing?.name.orEmpty(),
    email = email ?: existing?.email.orEmpty(),
    mobile = mobile ?: existing?.mobile.orEmpty(),
    geminiApiKey = (geminiKey ?: existing?.geminiApiKey)?.trim().takeUnless { it.isNullOrBlank() },
    theme = theme ?: existing?.theme,
    imagePath = existing?.imagePath,
)
