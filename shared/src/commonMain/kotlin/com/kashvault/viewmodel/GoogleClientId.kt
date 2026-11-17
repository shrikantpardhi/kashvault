package com.kashvault.viewmodel

expect fun bundledGoogleClientId(): String

/** OAuth client used for token refresh. iOS prefers GIDClientID (native client, PKCE, no secret). */
expect fun platformOAuthClientId(webClientId: String): String
