package com.kashvault.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kashvault.ui.rememberSingleClick
import com.kashvault.ui.components.BrandMark
import com.kashvault.ui.components.vaultGradient
import com.kashvault.ui.haptics.platformHapticTick
import com.kashvault.ui.theme.FabSize
import com.kashvault.ui.screens.AddExpenseScreen
import com.kashvault.ui.screens.AdvisorPane
import com.kashvault.ui.screens.AdvisorScreen
import com.kashvault.ui.screens.AiSettingsScreen
import com.kashvault.ui.screens.AppLockScreen
import com.kashvault.ui.screens.BackupScreen
import com.kashvault.ui.screens.BudgetScreen
import com.kashvault.ui.screens.DashboardPane
import com.kashvault.ui.screens.DashboardScreen
import com.kashvault.ui.screens.InboxScreen
import com.kashvault.ui.screens.LendingPane
import com.kashvault.ui.screens.LendingScreen
import com.kashvault.ui.screens.LocalFinanceViewModel
import com.kashvault.ui.screens.LoginScreen
import com.kashvault.ui.screens.PersonLendingScreen
import com.kashvault.ui.screens.PersonalDetailsScreen
import com.kashvault.ui.screens.PrivacyPolicyScreen
import com.kashvault.ui.screens.ProfileScreen
import com.kashvault.ui.screens.ScanKeywordsScreen
import com.kashvault.ui.screens.SetupScreen
import com.kashvault.ui.screens.WealthPane
import com.kashvault.ui.screens.WealthScreen

data class NavItem(val label: String, val icon: ImageVector, val screen: Screen)

fun bottomNavItemsFor(): List<NavItem> = listOf(
    NavItem("Home", Icons.Default.Home, DashboardScreen),
    NavItem("Wealth", Icons.AutoMirrored.Filled.TrendingUp, WealthScreen),
    NavItem("AI", Icons.Default.Psychology, AdvisorScreen),
    NavItem("Lending", Icons.Default.Handshake, LendingScreen),
)

val bottomNavItems = listOf(
    NavItem("Home", Icons.Default.Home, DashboardScreen),
    NavItem("Wealth", Icons.AutoMirrored.Filled.TrendingUp, WealthScreen),
    NavItem("AI", Icons.Default.Psychology, AdvisorScreen),
    NavItem("Lending", Icons.Default.Handshake, LendingScreen),
)

object MainTabs {
    var selectedKey by mutableStateOf(DashboardScreen.key)
        private set

    fun select(key: String) {
        selectedKey = key
    }

    fun reset() {
        selectedKey = DashboardScreen.key
    }

    fun selectedScreen(): Screen {
        val items = bottomNavItemsFor()
        return items.firstOrNull { it.screen.key == selectedKey }?.screen ?: DashboardScreen
    }
}

private val LocalEmbedInAppChrome = staticCompositionLocalOf { false }

object TabsScreen : Screen {
    override val key = "main-tabs"

    @Composable
    override fun Content() {
        val selected = MainTabs.selectedScreen()
        KashVaultScaffold(currentScreen = selected, title = chromeTitle(selected)) { modifier ->
            Box(modifier.fillMaxSize()) {
                when (MainTabs.selectedKey) {
                    WealthScreen.key -> WealthPane(Modifier.fillMaxSize())
                    AdvisorScreen.key -> AdvisorPane(Modifier.fillMaxSize())
                    LendingScreen.key -> LendingPane(Modifier.fillMaxSize())
                    else -> DashboardPane(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
internal fun AppNavigatorHost(navigator: Navigator) {
    val current = navigator.lastItem
    if (current is LoginScreen || current is SetupScreen) {
        current.Content()
        return
    }
    val visualScreen = if (current.key == TabsScreen.key) MainTabs.selectedScreen() else current
    CompositionLocalProvider(LocalEmbedInAppChrome provides true) {
        AppChromeScaffold(
            currentScreen = visualScreen,
            title = chromeTitle(visualScreen),
        ) { modifier ->
            Box(modifier.fillMaxSize()) {
                current.Content()
            }
        }
    }
}

internal fun chromeTitle(screen: Screen): String = when (screen) {
    is AddExpenseScreen -> when {
        screen.duplicate -> "Duplicate"
        screen.expenseId != null -> "Edit"
        else -> "Add"
    }
    is PersonLendingScreen -> screen.person
    else -> when (screen.key) {
        DashboardScreen.key, TabsScreen.key -> "KashVault"
        WealthScreen.key -> "Wealth"
        AdvisorScreen.key -> "AI"
        LendingScreen.key -> "Lending"
        "history" -> "History"
        "inbox" -> "Inbox"
        "setup" -> "Set up"
        "bills" -> "Bills & salary"
        "goals" -> "Goals"
        "loans" -> "Loans"
        "recurring" -> "Recurring"
        "categories", "categories-alias" -> "Categories"
        "payment-sources" -> "Banks & cards"
        "budget" -> "Budget"
        "profile" -> "Settings"
        "personal-details" -> "Profile"
        "backup" -> "Backup"
        "scan-keywords" -> "Message scan"
        "appearance" -> "Appearance"
        "app-lock" -> "App lock"
        "ai-settings" -> "AI settings"
        "privacy-policy" -> "Privacy & Safety"
        else -> "KashVault"
    }
}

private fun Screen.isNewAddScreen(): Boolean =
    this is AddExpenseScreen && expenseId == null && !duplicate

private fun Screen.isSettingsFamily(): Boolean = when (key) {
    ProfileScreen.key,
    PersonalDetailsScreen.key,
    BackupScreen.key,
    ScanKeywordsScreen.key,
    AppLockScreen.key,
    AiSettingsScreen.key,
    BudgetScreen.key,
    PrivacyPolicyScreen.key,
    -> true
    else -> false
}

private fun Screen.isMainTab(): Boolean = bottomNavItems.any { it.screen.key == key }

internal fun Navigator.openMainTab(tab: Screen) {
    MainTabs.select(tab.key)
    if (lastItem.key == TabsScreen.key) return
    if (items.any { it.key == TabsScreen.key }) {
        popUntil { it.key == TabsScreen.key }
    } else {
        replaceAll(TabsScreen)
    }
}

@Composable
private fun tabItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun BottomNavigationBar(currentScreen: Screen) {
    val navigator = LocalNavigator.currentOrThrow
    val activeNavItems = bottomNavItemsFor()
    val selectedTabKey = if (currentScreen.isMainTab()) currentScreen.key else null
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
    ) {
        activeNavItems.forEach { item ->
            val itemColors = tabItemColors()
            NavigationBarItem(
                selected = selectedTabKey == item.screen.key,
                onClick = { navigator.openMainTab(item.screen) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                alwaysShowLabel = false,
                colors = itemColors,
            )
        }
    }
}

@Composable
fun KashVaultScaffold(
    currentScreen: Screen,
    title: String,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    if (LocalEmbedInAppChrome.current) {
        content(Modifier.fillMaxSize())
        return
    }
    AppChromeScaffold(currentScreen, title, toolbarActions, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppChromeScaffold(
    currentScreen: Screen,
    title: String,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val isTab = currentScreen.isMainTab() || currentScreen.key == TabsScreen.key
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(size = 28, onBrand = false)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (!isTab && navigator.canPop) {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    toolbarActions()
                    if (currentScreen.key != InboxScreen.key) {
                        InboxHeaderButton(
                            onClick = { navigator.push(InboxScreen) },
                        )
                    }
                    if (!currentScreen.isSettingsFamily()) {
                        IconButton(onClick = { navigator.push(ProfileScreen) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        bottomBar = { if (!imeVisible) BottomNavigationBar(currentScreen) },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun InboxHeaderButton(onClick: () -> Unit) {
    val vm = LocalFinanceViewModel.current
    val pending by vm.state.collectAsState()
    val count = pending.inboxItems.size
    Box {
        IconButton(onClick = onClick) {
            Icon(Icons.Default.Notifications, contentDescription = "Inbox")
        }
        if (count > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp, 4.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(if (count > 99) "99+" else count.toString())
            }
        }
    }
}
