package com.kashvault.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import com.kashvault.auth.AndroidAppContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REQUEST_CODE_DEVICE_AUTH = 0xF001

object DeviceAuthAndroid {
    var pending: CompletableDeferred<Boolean>? = null
    fun complete(resultCode: Int) {
        pending?.complete(resultCode == Activity.RESULT_OK)
        pending = null
    }
}

actual suspend fun requestDeviceAuth(prompt: String): Boolean {
    val activity = AndroidAppContext.activity ?: return false
    val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        ?: return false
    if (!km.isDeviceSecure) return false
    @Suppress("DEPRECATION")
    val intent = km.createConfirmDeviceCredentialIntent(prompt, null) ?: return false
    val deferred = CompletableDeferred<Boolean>()
    DeviceAuthAndroid.pending = deferred
    withContext(Dispatchers.Main) {
        activity.startActivityForResult(intent, REQUEST_CODE_DEVICE_AUTH)
    }
    return deferred.await()
}

actual fun isDeviceAuthAvailable(): Boolean {
    val context = AndroidAppContext.appContext ?: return false
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
    return km.isDeviceSecure
}
