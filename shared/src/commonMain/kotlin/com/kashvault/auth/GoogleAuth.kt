package com.kashvault.auth

interface GoogleAuth {
    suspend fun signIn(clientId: String): GoogleUser
    suspend fun signOut()
    suspend fun freshAccessToken(
        clientId: String,
        stored: StoredOAuth,
        forceRefresh: Boolean = false,
    ): OAuthTokens
}

expect fun createGoogleAuth(): GoogleAuth
