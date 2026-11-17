package com.kashvault.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kashvault.auth.AndroidAppContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual suspend fun requestDeviceAuth(prompt: String): Boolean {
    val activity = AndroidAppContext.activity as? FragmentActivity ?: return false
    if (!isDeviceAuthAvailable()) return false
    return suspendCancellableCoroutine { continuation ->
        lateinit var biometricPrompt: BiometricPrompt
        biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
        continuation.invokeOnCancellation { biometricPrompt.cancelAuthentication() }
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock KashVault")
                .setSubtitle(prompt)
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }
}

actual fun isDeviceAuthAvailable(): Boolean {
    val context = AndroidAppContext.appContext ?: return false
    return BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK,
    ) == BiometricManager.BIOMETRIC_SUCCESS
}
