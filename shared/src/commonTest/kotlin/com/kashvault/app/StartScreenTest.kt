package com.kashvault.app

import com.kashvault.domain.Expense
import com.kashvault.domain.FinanceSnapshot
import com.kashvault.domain.GoogleSession
import com.kashvault.domain.PaymentSource
import com.kashvault.ui.navigation.TabsScreen
import com.kashvault.ui.screens.LoginScreen
import com.kashvault.ui.screens.SetupScreen
import kotlin.test.Test
import kotlin.test.assertEquals

class StartScreenTest {
    @Test
    fun emptyVaultGoesToLogin() {
        assertEquals(LoginScreen, startScreenForSnapshot(FinanceSnapshot()))
    }

    @Test
    fun signedInWithoutSetupGoesToSetup() {
        val snapshot = FinanceSnapshot(
            authChoice = "google",
            googleSession = GoogleSession(
                userId = "u1",
                email = "a@b.com",
                displayName = "A",
            ),
        )
        assertEquals(SetupScreen, startScreenForSnapshot(snapshot))
    }

    @Test
    fun vaultWithSourcesGoesHome() {
        val snapshot = FinanceSnapshot(
            authChoice = "local",
            paymentSources = listOf(
                PaymentSource(id = "src1", name = "HDFC", kind = "bank"),
            ),
            expenses = listOf(
                Expense(
                    id = "e1",
                    amount = 10.0,
                    categoryId = "cat",
                    timestamp = "2026-01-01T00:00:00Z",
                    type = "expense",
                ),
            ),
        )
        assertEquals(TabsScreen, startScreenForSnapshot(snapshot))
    }

    @Test
    fun completedSetupGoesHomeEvenWithoutSources() {
        val snapshot = FinanceSnapshot(authChoice = "local", setupCompleted = true)
        assertEquals(TabsScreen, startScreenForSnapshot(snapshot))
    }
}
