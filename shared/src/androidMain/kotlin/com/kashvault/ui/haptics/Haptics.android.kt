package com.kashvault.ui.haptics

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.kashvault.auth.AndroidAppContext

actual fun platformHapticTick() {
    runCatching {
        val context = AndroidAppContext.appContext ?: return
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12)
        }
    }
}
