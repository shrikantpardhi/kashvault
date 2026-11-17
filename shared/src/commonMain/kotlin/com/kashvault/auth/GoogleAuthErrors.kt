package com.kashvault.auth

enum class GoogleSignInFailureKind {
    CANCELLED,
    NO_CREDENTIAL,
    CONFIG,
    NETWORK,
    EXPIRED,
    UNKNOWN,
}

fun googleSignInFailureKind(message: String?): GoogleSignInFailureKind {
    val text = message.orEmpty().lowercase()
    if (text.isBlank()) return GoogleSignInFailureKind.UNKNOWN
    if (
        text.contains("cancel") ||
        text.contains("cancelled") ||
        text.contains("canceled") ||
        text.contains("12501")
    ) {
        if (text.contains("begin sign in") || text.contains("no credential") || text.contains("cannot find")) {
            return GoogleSignInFailureKind.NO_CREDENTIAL
        }
        return GoogleSignInFailureKind.CANCELLED
    }
    if (
        text.contains("no credential") ||
        text.contains("cannot find a matching credential") ||
        text.contains("cannot find") ||
        text.contains("16:")
    ) {
        return GoogleSignInFailureKind.NO_CREDENTIAL
    }
    if (
        text.contains("developer_error") ||
        text.contains("10:") ||
        text.contains("console is not set up") ||
        text.contains("invalid_client") ||
        text.contains("unauthorized_client") ||
        text.contains("redirect_uri") ||
        text.contains("audience") ||
        text.contains("client id") && text.contains("invalid")
    ) {
        return GoogleSignInFailureKind.CONFIG
    }
    if (
        text.contains("network") ||
        text.contains("7:") ||
        text.contains("timeout") ||
        text.contains("unable to resolve")
    ) {
        return GoogleSignInFailureKind.NETWORK
    }
    if (
        text.contains("401") ||
        text.contains("invalid_grant") ||
        text.contains("unauthenticated")
    ) {
        return GoogleSignInFailureKind.EXPIRED
    }
    return GoogleSignInFailureKind.UNKNOWN
}

fun humanizeGoogleSignInError(
    message: String?,
    kind: GoogleSignInFailureKind = googleSignInFailureKind(message),
    signingSha1: String = platformSigningSha1(),
): String {
    return when (kind) {
        GoogleSignInFailureKind.CANCELLED -> "Sign-in was canceled."
        GoogleSignInFailureKind.NO_CREDENTIAL ->
            "Google could not find an account on this device. Check that Google Play services is signed in, then try again."
        GoogleSignInFailureKind.CONFIG -> {
            val shaClause = if (signingSha1.isNotBlank()) "SHA-1 $signingSha1" else "this app’s SHA-1"
            "Google Sign-In is not set up for this app build. Use a Web client ID as the audience, add an Android OAuth client " +
                "for package com.kashvault with $shaClause in the same Cloud project, and on iPhone use a Desktop client " +
                "with redirect com.kashvault:/oauth."
        }
        GoogleSignInFailureKind.NETWORK ->
            "Could not reach Google. Check the network and try again."
        GoogleSignInFailureKind.EXPIRED ->
            "Google needs another sign-in. Tap Sign in with Google and allow Drive access again."
        GoogleSignInFailureKind.UNKNOWN ->
            message?.takeIf { it.isNotBlank() && it.length < 220 }
                ?: "Failed to sign in with Google. Confirm the Web client ID and try again."
    }
}

fun looksLikeGoogleClientId(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.endsWith(".apps.googleusercontent.com") && trimmed.contains("-")
}
