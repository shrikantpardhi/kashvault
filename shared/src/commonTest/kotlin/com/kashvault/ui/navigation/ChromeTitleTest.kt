package com.kashvault.ui.navigation

import com.kashvault.ui.screens.AddExpenseScreen
import com.kashvault.ui.screens.AdvisorScreen
import com.kashvault.ui.screens.DashboardScreen
import com.kashvault.ui.screens.HistoryScreen
import com.kashvault.ui.screens.InboxScreen
import com.kashvault.ui.screens.PersonLendingScreen
import com.kashvault.ui.screens.ProfileScreen
import com.kashvault.ui.screens.ScanKeywordsScreen
import kotlin.test.Test
import kotlin.test.assertEquals

class ChromeTitleTest {
    @Test
    fun homeTabUsesBrandTitleSource() {
        assertEquals("KashVault", chromeTitle(DashboardScreen))
        assertEquals("KashVault", chromeTitle(TabsScreen))
        assertEquals("AI", chromeTitle(AdvisorScreen))
    }

    @Test
    fun nestedPagesKeepStableTitles() {
        assertEquals("History", chromeTitle(HistoryScreen))
        assertEquals("Settings", chromeTitle(ProfileScreen))
        assertEquals("Message scan", chromeTitle(ScanKeywordsScreen))
        assertEquals("App lock", chromeTitle(com.kashvault.ui.screens.AppLockScreen))
        assertEquals("AI settings", chromeTitle(com.kashvault.ui.screens.AiSettingsScreen))
        assertEquals("Categories", chromeTitle(com.kashvault.ui.screens.CategoryStudioScreen))
        assertEquals("Inbox", chromeTitle(InboxScreen))
        assertEquals("Add", chromeTitle(AddExpenseScreen()))
        assertEquals("Edit", chromeTitle(AddExpenseScreen(expenseId = "e1")))
        assertEquals("Duplicate", chromeTitle(AddExpenseScreen(expenseId = "e1", duplicate = true)))
        assertEquals("Budget", chromeTitle(com.kashvault.ui.screens.BudgetScreen))
        assertEquals("Asha", chromeTitle(PersonLendingScreen("Asha", "p1")))
    }
}
