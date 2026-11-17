package com.kashvault.viewmodel

actual fun bundledGoogleClientId(): String {
    return try {
        val clazz = Class.forName("com.kashvault.BuildConfig")
        val field = clazz.getField("GOOGLE_WEB_CLIENT_ID")
        (field.get(null) as? String).orEmpty()
    } catch (_: Exception) {
        try {
            val clazz = Class.forName("com.kashvault.shared.BuildConfig")
            val field = clazz.getField("GOOGLE_WEB_CLIENT_ID")
            (field.get(null) as? String).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}

actual fun platformOAuthClientId(webClientId: String): String = webClientId
