package com.kashvault.profile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createPlatformImagePicker(): PlatformImagePicker = IosImagePicker()

private class IosImagePicker : PlatformImagePicker {
    private var pickerDelegate: NSObject? = null

    override suspend fun pickImage(): ByteArray? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            picker.allowsEditing = true
            val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>,
                ) {
                    pickerDelegate = null
                    val image = (
                        didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                            ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]
                        ) as? UIImage
                    picker.dismissViewControllerAnimated(true, completion = null)
                    continuation.resume(image?.toJpegBytes())
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    pickerDelegate = null
                    picker.dismissViewControllerAnimated(true, completion = null)
                    continuation.resume(null)
                }
            }
            pickerDelegate = delegate
            picker.delegate = delegate
            val host = presenter()
            if (host == null) {
                pickerDelegate = null
                continuation.resumeWithException(IllegalStateException("Could not open the photo picker."))
                return@suspendCancellableCoroutine
            }
            host.presentViewController(picker, animated = true, completion = null)
        }
    }

    private fun presenter(): UIViewController? {
        val window = UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow
        return window?.rootViewController
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toJpegBytes(): ByteArray? {
    val prepared = scaledTo(512.0)
    val data = UIImageJPEGRepresentation(prepared, 0.85) ?: return null
    val length = data.length.toInt()
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.scaledTo(maxEdge: Double): UIImage {
    val width = size.useContents { width }
    val height = size.useContents { height }
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return this
    val scale = maxEdge / longest
    val newWidth = width * scale
    val newHeight = height * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(newWidth, newHeight), false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, newWidth, newHeight))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return scaled ?: this
}
