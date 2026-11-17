package com.kashvault.auth

import com.kashvault.domain.FinanceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountVaultTest {
    @Test
    fun googleUserIdBecomesStableParkKey() {
        assertEquals("g_123-abc_user", vaultAccountKey("123-abc_user"))
        assertEquals("g_ab_cd", vaultAccountKey("ab/cd"))
        assertEquals("local", vaultAccountKey(" "))
        assertEquals("local", vaultAccountKey(null))
    }

    @Test
    fun linkingLocalWithDataKeepsVault() {
        assertEquals(
            GoogleSignInDataPlan.LinkLocal,
            planGoogleSignIn(currentAuthChoice = "local", hasCurrentUserData = true, parkedForThisUser = true),
        )
    }

    @Test
    fun sameGoogleAccountReopensParkedVault() {
        assertEquals(
            GoogleSignInDataPlan.OpenParked,
            planGoogleSignIn(currentAuthChoice = null, hasCurrentUserData = false, parkedForThisUser = true),
        )
    }

    @Test
    fun firstGoogleLoginStartsEmpty() {
        assertEquals(
            GoogleSignInDataPlan.FreshEmpty,
            planGoogleSignIn(currentAuthChoice = null, hasCurrentUserData = false, parkedForThisUser = false),
        )
        assertEquals(
            GoogleSignInDataPlan.FreshEmpty,
            planGoogleSignIn(currentAuthChoice = "local", hasCurrentUserData = false, parkedForThisUser = false),
        )
    }

    @Test
    fun overlayKeepsParkedRefreshWhenLiveSignInHasOnlyIdToken() {
        val parked = com.kashvault.domain.GoogleSession(
            userId = "u1",
            email = "a@b.com",
            accessToken = "parked-access",
            refreshToken = "parked-refresh",
            expiresAtEpochMs = 99L,
        )
        val live = com.kashvault.domain.GoogleSession(
            userId = "u1",
            displayName = "A",
            idToken = "new-id",
        )
        val merged = overlayGoogleSession(live, parked)!!
        assertEquals(null, merged.accessToken)
        assertEquals("parked-refresh", merged.refreshToken)
        assertEquals("new-id", merged.idToken)
        assertEquals("A", merged.displayName)
        assertEquals(null, merged.expiresAtEpochMs)
    }

    @Test
    fun overlayDoesNotReuseAnotherAccountsTokens() {
        val parked = com.kashvault.domain.GoogleSession(userId = "other", accessToken = "x")
        val live = com.kashvault.domain.GoogleSession(userId = "u1", idToken = "id")
        val merged = overlayGoogleSession(live, parked)!!
        assertEquals("u1", merged.userId)
        assertEquals(null, merged.accessToken)
    }

    @Test
    fun overlayKeepsParkedAccessWhenIdTokenDidNotChange() {
        val parked = com.kashvault.domain.GoogleSession(
            userId = "u1",
            accessToken = "parked-access",
            refreshToken = "parked-refresh",
            idToken = "same-id",
            expiresAtEpochMs = 99L,
        )
        val live = com.kashvault.domain.GoogleSession(
            userId = "u1",
            idToken = "same-id",
        )
        val merged = overlayGoogleSession(live, parked)!!
        assertEquals("parked-access", merged.accessToken)
        assertEquals("parked-refresh", merged.refreshToken)
        assertEquals(99L, merged.expiresAtEpochMs)
    }

    @Test
    fun snapshotWithSourcesCountsAsUserData() {
        assertFalse(hasUserFinanceData(FinanceSnapshot()))
        assertTrue(
            hasUserFinanceData(
                FinanceSnapshot(
                    paymentSources = listOf(
                        com.kashvault.domain.PaymentSource(id = "s1", name = "HDFC", kind = "bank"),
                    ),
                ),
            ),
        )
    }
}
