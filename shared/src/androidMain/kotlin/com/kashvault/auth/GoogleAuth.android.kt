package com.kashvault.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object AndroidAppContext {
    var activity: Activity? = null
    @Volatile
    var application: android.content.Context? = null
    val appContext: android.content.Context?
        get() = application ?: activity?.applicationContext
}

actual fun createGoogleAuth(): GoogleAuth = AndroidGoogleAuth()

data class GoogleAuthorizationGrant(
    val accessToken: String = "",
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val imageUrl: String? = null,
    val idToken: String? = null,
)

interface DriveAuthorizationHost {
    fun launchDriveAuthorization(clientId: String, result: kotlinx.coroutines.CompletableDeferred<String>)
    fun launchGoogleAuthorization(
        clientId: String,
        scopes: List<String>,
        result: kotlinx.coroutines.CompletableDeferred<GoogleAuthorizationGrant>,
    )
}

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/** AuthorizationClient needs full scope URIs. Short names like "email" produce tokens that 401 on userinfo. */
private val USERINFO_AND_DRIVE_SCOPES = listOf(
    "https://www.googleapis.com/auth/userinfo.email",
    "https://www.googleapis.com/auth/userinfo.profile",
    DRIVE_APPDATA_SCOPE,
)

private val DRIVE_ONLY_SCOPES = listOf(DRIVE_APPDATA_SCOPE)

private class AndroidGoogleAuth : GoogleAuth {
    override suspend fun signIn(clientId: String): GoogleUser {
        val activity = AndroidAppContext.activity
            ?: throw GoogleAuthException("Google Sign-In is not ready yet. Keep the app open and try again.")
        if (clientId.isBlank()) {
            throw GoogleAuthException(
                "Add your Google Web client ID (ends with .apps.googleusercontent.com) to enable Sign-In.",
            )
        }
        val manager = CredentialManager.create(activity)

        val first = runCatching {
            requestCredential(manager, activity, signInButtonRequest(clientId))
        }
        first.getOrNull()?.let { return attachDriveAccessIfPossible(clientId, it) }

        val firstError = first.exceptionOrNull()!!
        if (isExplicitUserCancel(firstError)) {
            throw GoogleAuthException(humanizeGoogleSignInError(firstError.message, GoogleSignInFailureKind.CANCELLED), firstError)
        }

        val second = runCatching {
            requestCredential(manager, activity, googleIdRequest(clientId))
        }
        second.getOrNull()?.let { return attachDriveAccessIfPossible(clientId, it) }

        val secondError = second.exceptionOrNull()
        if (secondError != null && isExplicitUserCancel(secondError)) {
            throw GoogleAuthException(humanizeGoogleSignInError(secondError.message, GoogleSignInFailureKind.CANCELLED), secondError)
        }

        return try {
            signInWithPlayServices(clientId)
        } catch (cancelled: GetCredentialCancellationException) {
            throw GoogleAuthException(humanizeGoogleSignInError(cancelled.message, GoogleSignInFailureKind.CANCELLED), cancelled)
        } catch (error: GoogleAuthException) {
            throw error
        } catch (error: Exception) {
            val preferred = secondError ?: firstError
            throw GoogleAuthException(
                humanizeGoogleSignInError(error.message ?: preferred.message),
                error,
            )
        }
    }

    override suspend fun signOut() {
        val activity = AndroidAppContext.activity ?: return
        runCatching {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    override suspend fun freshAccessToken(
        clientId: String,
        stored: StoredOAuth,
        forceRefresh: Boolean,
    ): OAuthTokens {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (!forceRefresh &&
            accessTokenIsUsable(stored.accessToken, stored.expiresAtEpochMs, now)
        ) {
            return OAuthTokens(
                accessToken = stored.accessToken!!,
                refreshToken = stored.refreshToken,
                idToken = stored.idToken,
                expiresAtEpochMs = stored.expiresAtEpochMs,
            )
        }
        if (!stored.refreshToken.isNullOrBlank()) {
            val refreshed = runCatching {
                GoogleTokenApi().refresh(clientId, stored.refreshToken)
            }.getOrNull()
            if (refreshed != null) return refreshed
        }
        if (AndroidAppContext.activity as? DriveAuthorizationHost == null) {
            val existing = stored.accessToken?.takeIf { it.isNotBlank() }
                ?: throw GoogleAuthException("Google Drive authorization is not ready.")
            return OAuthTokens(
                accessToken = existing,
                refreshToken = stored.refreshToken,
                idToken = stored.idToken,
                expiresAtEpochMs = stored.expiresAtEpochMs,
            )
        }
        val grant = requestGrant(clientId, DRIVE_ONLY_SCOPES)
        if (grant.accessToken.isBlank()) {
            throw GoogleAuthException("Google Drive authorization is not ready.")
        }
        return OAuthTokens(
            accessToken = grant.accessToken,
            refreshToken = stored.refreshToken,
            idToken = stored.idToken ?: grant.idToken,
            expiresAtEpochMs = expiresAtEpochMs(now, expiresInSeconds = 3_500),
        )
    }

    private suspend fun signInWithPlayServices(clientId: String): GoogleUser {
        val grant = requestGrant(clientId, USERINFO_AND_DRIVE_SCOPES)
        val fromGrant = userFromGrant(grant)
        if (fromGrant != null) {
            return fromGrant
        }
        if (grant.accessToken.isBlank()) {
            throw GoogleAuthException("Google signed in, but did not return a user ID.")
        }
        val profile = runCatching { GoogleTokenApi().fetchProfile(grant.accessToken) }.getOrNull()
        val userId = profile?.sub?.takeIf { it.isNotBlank() }
            ?: throw GoogleAuthException("Google signed in, but did not return a user ID.")
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return GoogleUser(
            userId = userId,
            email = profile?.email ?: grant.email,
            displayName = profile?.name ?: grant.displayName,
            imageUrl = profile?.picture ?: grant.imageUrl,
            accessToken = grant.accessToken,
            idToken = grant.idToken,
            expiresAtEpochMs = expiresAtEpochMs(now, 3_500),
        )
    }

    private suspend fun requestGrant(clientId: String, scopes: List<String>): GoogleAuthorizationGrant {
        val host = AndroidAppContext.activity as? DriveAuthorizationHost
            ?: throw GoogleAuthException("Google Sign-In is not ready yet.")
        val result = kotlinx.coroutines.CompletableDeferred<GoogleAuthorizationGrant>()
        withContext(Dispatchers.Main) {
            host.launchGoogleAuthorization(clientId, scopes, result)
        }
        return result.await()
    }

    private fun userFromGrant(grant: GoogleAuthorizationGrant): GoogleUser? {
        val userId = grant.userId?.takeIf { it.isNotBlank() } ?: return null
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return GoogleUser(
            userId = userId,
            email = grant.email,
            displayName = grant.displayName,
            imageUrl = grant.imageUrl,
            accessToken = grant.accessToken,
            idToken = grant.idToken,
            expiresAtEpochMs = expiresAtEpochMs(now, 3_500),
        )
    }

    private suspend fun attachDriveAccessIfPossible(clientId: String, user: GoogleUser): GoogleUser {
        val tokens = runCatching {
            freshAccessToken(
                clientId = clientId,
                stored = StoredOAuth(
                    accessToken = user.accessToken,
                    refreshToken = user.refreshToken,
                    idToken = user.idToken,
                    expiresAtEpochMs = user.expiresAtEpochMs,
                ),
                forceRefresh = user.accessToken.isNullOrBlank(),
            )
        }.getOrNull() ?: return user
        return user.copy(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: user.refreshToken,
            idToken = tokens.idToken ?: user.idToken,
            expiresAtEpochMs = tokens.expiresAtEpochMs,
        )
    }

    private suspend fun requestCredential(
        manager: CredentialManager,
        activity: Activity,
        request: GetCredentialRequest,
    ): GoogleUser {
        val result = manager.getCredential(context = activity, request = request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val claims = decodeIdToken(google.idToken)
            val email = claims.optString("email").ifBlank {
                google.id.takeIf { it.contains("@") }
            }
            return GoogleUser(
                userId = claims.optString("sub").ifBlank { google.id },
                email = email,
                displayName = google.displayName ?: claims.optString("name").ifBlank { null },
                imageUrl = google.profilePictureUri?.toString() ?: claims.optString("picture").ifBlank { null },
                idToken = google.idToken,
            )
        }
        throw GoogleAuthException("Google did not return an ID token.")
    }
}

private fun isExplicitUserCancel(error: Throwable): Boolean {
    if (error !is GetCredentialCancellationException) return false
    val text = error.message.orEmpty().lowercase()
    if (text.contains("begin sign in") || text.contains("no credential") || text.contains("cannot find")) {
        return false
    }
    return true
}

private fun googleIdRequest(clientId: String): GetCredentialRequest {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(false)
        .setNonce(UUID.randomUUID().toString())
        .build()
    return GetCredentialRequest.Builder().addCredentialOption(option).build()
}

private fun signInButtonRequest(clientId: String): GetCredentialRequest {
    val option = GetSignInWithGoogleOption.Builder(clientId)
        .setNonce(UUID.randomUUID().toString())
        .build()
    return GetCredentialRequest.Builder().addCredentialOption(option).build()
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeIdToken(idToken: String): JSONObject {
    val payload = idToken.split(".").getOrNull(1) ?: return JSONObject()
    val padded = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=')
    return try {
        val json = Base64.UrlSafe.decode(padded).decodeToString()
        JSONObject(json)
    } catch (_: Exception) {
        JSONObject()
    }
}
