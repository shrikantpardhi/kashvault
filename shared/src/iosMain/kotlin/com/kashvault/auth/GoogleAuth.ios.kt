package com.kashvault.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

actual fun createGoogleAuth(): GoogleAuth = IosGoogleAuth()

private class IosGoogleAuth : GoogleAuth {
    private val presenter = WebAuthPresenter()
    private val tokens = GoogleTokenApi(HttpClient(Darwin))
    private var authSession: ASWebAuthenticationSession? = null

    override suspend fun signIn(clientId: String): GoogleUser {
        val oauthClientId = oauthClientId(clientId)
        if (oauthClientId.isBlank()) {
            throw GoogleAuthException(
                "Add a Google OAuth client ID. On iPhone use a Desktop client with redirect com.kashvault:/oauth " +
                    "(GIDClientID in Info.plist), not a Web client.",
            )
        }
        val tokenSet = try {
            signInWithPkce(oauthClientId)
        } catch (error: Exception) {
            val text = error.message.orEmpty().lowercase()
            if (text.contains("cancel")) {
                throw GoogleAuthException(humanizeGoogleSignInError(error.message, GoogleSignInFailureKind.CANCELLED), error)
            }
            if (hasNativeIosClient()) {
                throw GoogleAuthException(humanizeGoogleSignInError(error.message), error)
            }
            try {
                signInImplicit(oauthClientId)
            } catch (implicit: Exception) {
                throw GoogleAuthException(
                    humanizeGoogleSignInError(error.message) +
                        " On iPhone, set GIDClientID to a Desktop OAuth client with redirect com.kashvault:/oauth. " +
                        "Web clients cannot finish Sign-In without a backend secret.",
                    error,
                )
            }
        }
        return withContext(Dispatchers.Default) {
            profileFromAccessToken(tokenSet, oauthClientId)
        }
    }

    override suspend fun signOut() = Unit

    override suspend fun freshAccessToken(
        clientId: String,
        stored: StoredOAuth,
        forceRefresh: Boolean,
    ): OAuthTokens {
        val oauthClientId = oauthClientId(clientId)
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
            return tokens.refresh(oauthClientId, stored.refreshToken)
        }
        if (oauthClientId.isBlank()) {
            throw GoogleAuthException("Sign in with Google to refresh the Drive session.")
        }
        return try {
            signInWithPkce(oauthClientId)
        } catch (error: Exception) {
            if (error.message.orEmpty().lowercase().contains("cancel") || hasNativeIosClient()) throw error
            signInImplicit(oauthClientId)
        }
    }

    private suspend fun signInWithPkce(clientId: String): OAuthTokens {
        val verifier = Pkce.verifier()
        val challenge = Pkce.challengeS256(verifier)
        val state = Pkce.verifier()
        val authUrl =
            "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${clientId.encodeURLParameter()}" +
                "&redirect_uri=${INSTALLED_APP_REDIRECT_URI.encodeURLParameter()}" +
                "&response_type=code" +
                "&scope=${"email profile $DRIVE_SCOPE".encodeURLParameter()}" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&include_granted_scopes=true" +
                "&state=$state"
        val callbackUrl = authenticate(authUrl)
        val params = queryParams(callbackUrl)
        val error = params["error"]
        if (!error.isNullOrBlank()) {
            throw GoogleAuthException(params["error_description"] ?: "Google sign-in failed ($error).")
        }
        val returnedState = params["state"]
        if (returnedState != state) {
            throw GoogleAuthException("Google sign-in state did not match.")
        }
        val code = params["code"]
            ?: throw GoogleAuthException("Google sign-in completed, but no authorization code was returned.")
        return try {
            tokens.exchangeAuthorizationCode(clientId, code, INSTALLED_APP_REDIRECT_URI, verifier)
        } catch (error: Exception) {
            throw GoogleAuthException(
                "Could not finish Google Sign-In on this device. Use a Desktop OAuth client " +
                    "(not a Web client) with redirect $INSTALLED_APP_REDIRECT_URI. KashVault has no backend " +
                    "and does not use a client secret. ${error.message ?: ""}".trim(),
                error,
            )
        }
    }

    private suspend fun signInImplicit(clientId: String): OAuthTokens {
        val authUrl =
            "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${clientId.encodeURLParameter()}" +
                "&redirect_uri=${INSTALLED_APP_REDIRECT_URI.encodeURLParameter()}" +
                "&response_type=token" +
                "&scope=${"email profile $DRIVE_SCOPE".encodeURLParameter()}" +
                "&include_granted_scopes=true"
        val callbackUrl = authenticate(authUrl)
        val params = queryParams(callbackUrl)
        val accessToken = params["access_token"]
            ?: throw GoogleAuthException("Google sign-in completed, but no access token was returned.")
        val expiresIn = params["expires_in"]?.toIntOrNull()
        return OAuthTokens(
            accessToken = accessToken,
            expiresAtEpochMs = expiresAtEpochMs(
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                expiresIn,
            ),
        )
    }

    private suspend fun profileFromAccessToken(tokenSet: OAuthTokens, oauthClientId: String): GoogleUser {
        return try {
            googleUser(tokens.fetchProfile(tokenSet.accessToken), tokenSet)
        } catch (error: GoogleAuthException) {
            if (googleSignInFailureKind(error.message) != GoogleSignInFailureKind.EXPIRED) throw error
            val retried = try {
                signInWithPkce(oauthClientId)
            } catch (retryError: Exception) {
                if (retryError.message.orEmpty().lowercase().contains("cancel") || hasNativeIosClient()) {
                    throw error
                }
                signInImplicit(oauthClientId)
            }
            googleUser(tokens.fetchProfile(retried.accessToken), retried)
        }
    }

    private fun googleUser(body: GoogleUserInfo, tokenSet: OAuthTokens): GoogleUser {
        val userId = body.sub ?: throw GoogleAuthException("Google did not return a user ID.")
        return GoogleUser(
            userId = userId,
            email = body.email,
            displayName = body.name,
            imageUrl = body.picture,
            accessToken = tokenSet.accessToken,
            refreshToken = tokenSet.refreshToken,
            idToken = tokenSet.idToken,
            expiresAtEpochMs = tokenSet.expiresAtEpochMs,
        )
    }

    private suspend fun authenticate(url: String): String =
        suspendCancellableCoroutine { continuation ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                continuation.resumeWithException(GoogleAuthException("Invalid Google Sign-In URL."))
                return@suspendCancellableCoroutine
            }

            val session = ASWebAuthenticationSession(
                uRL = nsUrl,
                callbackURLScheme = INSTALLED_APP_REDIRECT_SCHEME,
            ) { callbackUrl: NSURL?, error: NSError? ->
                authSession = null
                when {
                    error != null -> continuation.resumeWithException(
                        GoogleAuthException(error.localizedDescription),
                    )
                    callbackUrl == null -> continuation.resumeWithException(
                        GoogleAuthException("Sign-in was canceled."),
                    )
                    else -> continuation.resume(callbackUrl.absoluteString ?: "")
                }
            }
            authSession = session
            session.presentationContextProvider = presenter
            session.prefersEphemeralWebBrowserSession = false
            if (!session.start()) {
                authSession = null
                continuation.resumeWithException(GoogleAuthException("Could not start Google Sign-In."))
            }
        }

    private fun oauthClientId(webClientId: String): String {
        val info = NSBundle.mainBundle.infoDictionary
        val iosClientId = (info?.get("GIDClientID") as? String).orEmpty().trim()
        return iosClientId.ifBlank { webClientId }
    }

    private fun hasNativeIosClient(): Boolean {
        val info = NSBundle.mainBundle.infoDictionary
        return (info?.get("GIDClientID") as? String).orEmpty().trim().isNotEmpty()
    }
}

private fun queryParams(callbackUrl: String): Map<String, String> {
    val normalized = callbackUrl.replace("#", "?")
    val components = NSURLComponents(string = normalized)
    val items = components?.queryItems as? List<NSURLQueryItem> ?: return emptyMap()
    return items.mapNotNull { item ->
        val name = item.name ?: return@mapNotNull null
        name to (item.value ?: "")
    }.toMap()
}

private class WebAuthPresenter : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor {
        val windows = UIApplication.sharedApplication.windows
        val window = windows.firstOrNull() as? UIWindow
        return window ?: ASPresentationAnchor()
    }
}
