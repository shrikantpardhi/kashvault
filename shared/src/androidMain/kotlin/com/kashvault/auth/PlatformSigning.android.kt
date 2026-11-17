package com.kashvault.auth

import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

actual fun platformSigningSha1(): String {
    val context = AndroidAppContext.appContext ?: return ""
    return runCatching {
        val packageName = context.packageName
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return@runCatching ""
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        } ?: return@runCatching ""
        val bytes = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
        bytes.joinToString(":") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }.uppercase()
    }.getOrDefault("")
}
