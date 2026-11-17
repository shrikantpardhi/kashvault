package com.kashvault.auth

data class GoogleUser(
    val userId: String,
    val email: String? = null,
    val displayName: String? = null,
    val imageUrl: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val expiresAtEpochMs: Long? = null,
)

data class StoredOAuth(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val expiresAtEpochMs: Long? = null,
)

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val expiresAtEpochMs: Long? = null,
)

const val INSTALLED_APP_REDIRECT_SCHEME = "com.kashvault"
const val INSTALLED_APP_REDIRECT_URI = "com.kashvault:/oauth"

class GoogleAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)


/** Access tokens last about an hour. Treat them as stale a minute early. */
fun accessTokenIsUsable(
    accessToken: String?,
    expiresAtEpochMs: Long?,
    nowEpochMs: Long,
    skewMs: Long = 60_000L,
): Boolean {
    if (accessToken.isNullOrBlank()) return false
    if (expiresAtEpochMs == null) return true
    return expiresAtEpochMs > nowEpochMs + skewMs
}

fun expiresAtEpochMs(nowEpochMs: Long, expiresInSeconds: Int?, fallbackSeconds: Int = 3_500): Long {
    val seconds = expiresInSeconds?.takeIf { it > 0 } ?: fallbackSeconds
    return nowEpochMs + seconds * 1_000L
}
