package com.kashvault.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"

/**
 * Talks to Google from the phone. There is no KashVault backend and no client secret.
 * Public/installed OAuth clients (Android Play Services, iOS/Desktop + PKCE) are required.
 */
class GoogleTokenApi(
    private val http: HttpClient = HttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowEpochMs: () -> Long = { kotlinx.datetime.Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun exchangeAuthorizationCode(
        clientId: String,
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): OAuthTokens {
        val body = postToken(authorizationCodeTokenParams(clientId, code, redirectUri, codeVerifier))
        return body.toTokens(nowEpochMs())
    }

    suspend fun refresh(clientId: String, refreshToken: String): OAuthTokens {
        val body = postToken(refreshTokenParams(clientId, refreshToken))
        val tokens = body.toTokens(nowEpochMs())
        return tokens.copy(refreshToken = tokens.refreshToken ?: refreshToken)
    }

    suspend fun revoke(token: String) {
        if (token.isBlank()) return
        runCatching {
            http.post(REVOKE_URL) {
                setBody(
                    FormDataContent(
                        Parameters.build { append("token", token) },
                    ),
                )
            }
        }
    }

    suspend fun fetchProfile(accessToken: String): GoogleUserInfo {
        val response = http.get(USERINFO_URL) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (!response.status.isSuccess()) {
            throw GoogleAuthException("Could not load the Google profile (${response.status.value}).")
        }
        return json.decodeFromString<GoogleUserInfo>(response.bodyAsText())
    }

    private suspend fun postToken(params: Parameters): TokenResponseDto {
        val response = http.post(TOKEN_URL) {
            setBody(FormDataContent(params))
        }
        val raw = response.bodyAsText()
        val body = runCatching { json.decodeFromString<TokenResponseDto>(raw) }
            .getOrElse { throw GoogleAuthException("Google token response was not JSON.") }
        if (!response.status.isSuccess() || body.accessToken.isNullOrBlank()) {
            throw GoogleAuthException(
                body.errorDescription?.ifBlank { null }
                    ?: body.error?.ifBlank { null }
                    ?: "Google did not return an access token (${response.status.value}).",
            )
        }
        return body
    }
}

@Serializable
data class GoogleUserInfo(
    val sub: String? = null,
    val email: String? = null,
    val name: String? = null,
    val picture: String? = null,
)

@Serializable
internal data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

internal fun authorizationCodeTokenParams(
    clientId: String,
    code: String,
    redirectUri: String,
    codeVerifier: String,
): Parameters = Parameters.build {
    append("grant_type", "authorization_code")
    append("client_id", clientId)
    append("code", code)
    append("redirect_uri", redirectUri)
    append("code_verifier", codeVerifier)
}

internal fun refreshTokenParams(clientId: String, refreshToken: String): Parameters = Parameters.build {
    append("grant_type", "refresh_token")
    append("client_id", clientId)
    append("refresh_token", refreshToken)
}

internal fun TokenResponseDto.toTokens(nowEpochMs: Long): OAuthTokens {
    val access = accessToken ?: throw GoogleAuthException("Google did not return an access token.")
    return OAuthTokens(
        accessToken = access,
        refreshToken = refreshToken,
        idToken = idToken,
        expiresAtEpochMs = expiresAtEpochMs(nowEpochMs, expiresIn),
    )
}
