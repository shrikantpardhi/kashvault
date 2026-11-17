package com.kashvault.auth

import com.kashvault.domain.Constants
import com.kashvault.domain.FinanceSnapshot
import com.kashvault.domain.GoogleSession

enum class GoogleSignInDataPlan {
    LinkLocal,
    OpenParked,
    FreshEmpty,
}

fun vaultAccountKey(googleUserId: String?): String {
    val raw = googleUserId?.trim().orEmpty()
    if (raw.isEmpty()) return Constants.ACCOUNT_KEY_LOCAL
    val safe = buildString(raw.length.coerceAtMost(96)) {
        raw.take(96).forEach { ch ->
            append(if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_')
        }
    }
    return if (safe.isEmpty()) Constants.ACCOUNT_KEY_LOCAL else "g_$safe"
}

fun snapshotAccountKey(snapshot: FinanceSnapshot): String {
    val stored = snapshot.accountKey.trim()
    if (stored.isNotEmpty()) return stored
    return if (snapshot.googleSession != null) {
        vaultAccountKey(snapshot.googleSession.userId)
    } else {
        Constants.ACCOUNT_KEY_LOCAL
    }
}

fun overlayGoogleSession(live: GoogleSession?, parked: GoogleSession?): GoogleSession? {
    if (live == null) return parked
    if (parked == null || parked.userId != live.userId) return live
    val liveAccess = live.accessToken?.takeIf { it.isNotBlank() }
    val parkedAccess = parked.accessToken?.takeIf { it.isNotBlank() }
    val liveRefresh = live.refreshToken?.takeIf { it.isNotBlank() }
    val parkedRefresh = parked.refreshToken?.takeIf { it.isNotBlank() }
    val liveId = live.idToken?.takeIf { it.isNotBlank() }
    val parkedId = parked.idToken?.takeIf { it.isNotBlank() }
    // A new ID token means a new sign-in. Keep the parked refresh token, but do not
    // reuse the parked access token — it may already be revoked or rejected as 401.
    val signedInAgain = liveId != null && liveId != parkedId
    val access = liveAccess ?: parkedAccess.takeUnless { signedInAgain }
    return live.copy(
        email = live.email?.takeIf { it.isNotBlank() } ?: parked.email,
        displayName = live.displayName?.takeIf { it.isNotBlank() } ?: parked.displayName,
        imageUrl = live.imageUrl?.takeIf { it.isNotBlank() } ?: parked.imageUrl,
        accessToken = access,
        refreshToken = liveRefresh ?: parkedRefresh,
        idToken = liveId ?: parkedId,
        expiresAtEpochMs = when {
            liveAccess != null -> live.expiresAtEpochMs
            access != null -> parked.expiresAtEpochMs
            else -> live.expiresAtEpochMs
        },
    )
}

fun hasUserFinanceData(snapshot: FinanceSnapshot): Boolean =
    snapshot.expenses.isNotEmpty() ||
        snapshot.paymentSources.isNotEmpty() ||
        snapshot.borrowLend.isNotEmpty() ||
        snapshot.recurringBills.isNotEmpty() ||
        snapshot.loans.isNotEmpty() ||
        snapshot.investments.isNotEmpty() ||
        snapshot.insurances.isNotEmpty() ||
        snapshot.savingsGoals.isNotEmpty()

fun planGoogleSignIn(
    currentAuthChoice: String?,
    hasCurrentUserData: Boolean,
    parkedForThisUser: Boolean,
): GoogleSignInDataPlan {
    if (currentAuthChoice == "local" && hasCurrentUserData) return GoogleSignInDataPlan.LinkLocal
    if (parkedForThisUser) return GoogleSignInDataPlan.OpenParked
    return GoogleSignInDataPlan.FreshEmpty
}
