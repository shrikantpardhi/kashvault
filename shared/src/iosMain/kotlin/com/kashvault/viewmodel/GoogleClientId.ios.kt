package com.kashvault.viewmodel

import platform.Foundation.NSBundle

actual fun bundledGoogleClientId(): String {
    val info = NSBundle.mainBundle.infoDictionary ?: return ""
    val clientId = info["GIDClientID"] as? String
        ?: info["GOOGLE_WEB_CLIENT_ID"] as? String
    return clientId.orEmpty()
}

actual fun platformOAuthClientId(webClientId: String): String {
    val info = NSBundle.mainBundle.infoDictionary
    val iosClientId = (info?.get("GIDClientID") as? String).orEmpty().trim()
    return iosClientId.ifBlank { webClientId }
}
