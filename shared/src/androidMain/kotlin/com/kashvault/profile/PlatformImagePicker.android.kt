package com.kashvault.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kashvault.auth.AndroidAppContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

actual fun createPlatformImagePicker(): PlatformImagePicker = AndroidImagePicker()

interface ImagePickerHost {
    fun launchPickImage(result: CompletableDeferred<ByteArray?>)
}

private class AndroidImagePicker : PlatformImagePicker {
    override suspend fun pickImage(): ByteArray? {
        val host = AndroidAppContext.activity as? ImagePickerHost ?: return null
        val result = CompletableDeferred<ByteArray?>()
        withContext(Dispatchers.Main) { host.launchPickImage(result) }
        val bytes = result.await() ?: return null
        return withContext(Dispatchers.Default) { scaleJpeg(bytes) }
    }
}

private fun scaleJpeg(bytes: ByteArray, maxEdge: Int = 512, quality: Int = 85): ByteArray {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val longest = maxOf(original.width, original.height).coerceAtLeast(1)
    val scaled = if (longest <= maxEdge) {
        original
    } else {
        val factor = maxEdge.toFloat() / longest
        Bitmap.createScaledBitmap(
            original,
            (original.width * factor).roundToInt().coerceAtLeast(1),
            (original.height * factor).roundToInt().coerceAtLeast(1),
            true,
        )
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    return out.toByteArray()
}
