package com.kashvault.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createPlatformBackupActions(): PlatformBackupActions = IosBackupActions()

private class IosBackupActions : PlatformBackupActions {
    private var pickerDelegate: NSObject? = null

    override suspend fun exportToUser(bytes: ByteArray, filename: String) {
        val path = NSTemporaryDirectory() + filename
        writeFileBytes(path, bytes)
        val url = NSURL.fileURLWithPath(path)
        withContext(Dispatchers.Main) {
            val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            presenter()?.presentViewController(controller, animated = true, completion = null)
                ?: throw DatabaseBackupException("Could not open the iOS share sheet.")
        }
    }

    override suspend fun importFromUser(): ByteArray? = suspendCancellableCoroutine { continuation ->
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.data", "public.database", "public.item"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                pickerDelegate = null
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                val path = url?.path
                if (path == null) {
                    continuation.resume(null)
                    return
                }
                continuation.resume(readFileBytes(path))
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                pickerDelegate = null
                continuation.resume(null)
            }
        }
        pickerDelegate = delegate
        picker.delegate = delegate
        val host = presenter()
        if (host == null) {
            pickerDelegate = null
            continuation.resumeWithException(DatabaseBackupException("Could not open the iOS file picker."))
            return@suspendCancellableCoroutine
        }
        host.presentViewController(picker, animated = true, completion = null)
    }

    private fun presenter(): UIViewController? {
        val window = UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow
        return window?.rootViewController
    }
}
