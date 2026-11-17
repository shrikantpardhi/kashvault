package com.kashvault.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleOAuthTest {
    @Test
    fun accessTokenIsStaleAMinuteBeforeExpiry() {
        val now = 1_000_000L
        assertTrue(accessTokenIsUsable("ya29.token", now + 120_000L, now))
        assertFalse(accessTokenIsUsable("ya29.token", now + 30_000L, now))
        assertFalse(accessTokenIsUsable("", now + 120_000L, now))
        assertTrue(accessTokenIsUsable("ya29.token", expiresAtEpochMs = null, nowEpochMs = now))
    }

    @Test
    fun expiresAtUsesGoogleExpiresIn() {
        assertEquals(11_000L, expiresAtEpochMs(nowEpochMs = 1_000L, expiresInSeconds = 10))
        assertEquals(3_501_000L, expiresAtEpochMs(nowEpochMs = 1_000L, expiresInSeconds = null))
    }

    @Test
    fun pkceVerifierIsUrlSafeAndLongEnough() {
        val verifier = Pkce.verifier()
        assertTrue(verifier.length >= 43)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun pkceChallengeIsS256Base64Url() {
        val challenge = Pkce.challengeS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun tokenResponseKeepsRefreshTokenAndExpiry() {
        val tokens = TokenResponseDto(
            accessToken = "access",
            refreshToken = "refresh",
            expiresIn = 3600,
        ).toTokens(nowEpochMs = 0L)
        assertEquals("access", tokens.accessToken)
        assertEquals("refresh", tokens.refreshToken)
        assertEquals(3_600_000L, tokens.expiresAtEpochMs)
    }

    @Test
    fun tokenRequestsNeverIncludeAClientSecret() {
        val code = authorizationCodeTokenParams(
            clientId = "client.apps.googleusercontent.com",
            code = "4/code",
            redirectUri = INSTALLED_APP_REDIRECT_URI,
            codeVerifier = "verifier",
        )
        val refresh = refreshTokenParams("client.apps.googleusercontent.com", "1//refresh")
        assertEquals("authorization_code", code["grant_type"])
        assertEquals("refresh_token", refresh["grant_type"])
        assertEquals(null, code["client_secret"])
        assertEquals(null, refresh["client_secret"])
        assertEquals(INSTALLED_APP_REDIRECT_URI, code["redirect_uri"])
    }
}
