package com.kashvault.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleAuthErrorsTest {
    @Test
    fun cancellationIsFriendly() {
        assertEquals(GoogleSignInFailureKind.CANCELLED, googleSignInFailureKind("Sign-in was canceled."))
        assertEquals("Sign-in was canceled.", humanizeGoogleSignInError("canceled by the user"))
    }

    @Test
    fun beginSignInCancelIsTreatedAsMissingCredential() {
        assertEquals(
            GoogleSignInFailureKind.NO_CREDENTIAL,
            googleSignInFailureKind("During begin sign in, failure response from one tap: 16: Cannot find a matching credential."),
        )
    }

    @Test
    fun developerErrorMentionsShaAndClients() {
        val message = humanizeGoogleSignInError("10: Developer console is not set up correctly.")
        assertTrue(message.contains("SHA-1"))
        assertTrue(message.contains("Web client"))
    }

    @Test
    fun expiredAccessTokenIsFriendly() {
        assertEquals(GoogleSignInFailureKind.EXPIRED, googleSignInFailureKind("Could not load the Google profile (401)."))
        assertEquals(GoogleSignInFailureKind.EXPIRED, googleSignInFailureKind("invalid_grant"))
        assertTrue(humanizeGoogleSignInError("401 Unauthorized").contains("Sign in with Google"))
        assertEquals(GoogleSignInFailureKind.UNKNOWN, googleSignInFailureKind("Listing Drive database backups failed (403)."))
    }

    @Test
    fun webClientIdShape() {
        assertTrue(looksLikeGoogleClientId("123-abc.apps.googleusercontent.com"))
        assertFalse(looksLikeGoogleClientId("not-a-client"))
        assertFalse(looksLikeGoogleClientId(""))
    }
}
