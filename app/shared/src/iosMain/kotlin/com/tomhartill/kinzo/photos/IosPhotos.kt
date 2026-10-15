@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tomhartill.kinzo.photos

import kotlinx.cinterop.BetaInteropApi
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
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * UIImagePickerController: the system camera, or the photo library where there's no camera
 * (the simulator). Needs NSCameraUsageDescription in the app's Info.plist.
 */
class IosCamera : Camera {
    // The picker holds its delegate weakly, so it's kept here until the picker closes.
    private var delegate: PickerDelegate? = null

    override val hasCamera: Boolean
        get() = UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    override suspend fun takePhoto(maxEdge: Int): ByteArray? {
        val image = withContext(Dispatchers.Main) {
            val root = topViewController() ?: return@withContext null
            suspendCancellableCoroutine<UIImage?> { continuation ->
                val picker = UIImagePickerController()
                picker.sourceType = if (hasCamera) {
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                } else {
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                }
                val pickerDelegate = PickerDelegate { picked ->
                    delegate = null
                    picker.dismissViewControllerAnimated(true, completion = null)
                    if (continuation.isActive) continuation.resume(picked)
                }
                delegate = pickerDelegate
                picker.delegate = pickerDelegate
                root.presentViewController(picker, animated = true, completion = null)
            }
        } ?: return null
        return withContext(Dispatchers.Default) { image.scaledJpeg(maxEdge) }
    }

    override suspend fun resize(jpeg: ByteArray, maxEdge: Int): ByteArray = withContext(Dispatchers.Default) {
        val image = UIImage(data = jpeg.toNSData())
        image.scaledJpeg(maxEdge) ?: error("Couldn't read the photo")
    }
}

private class PickerDelegate(
    private val done: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        done(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        done(null)
    }
}

/**
 * Redraws the image at no more than [maxEdge] points on its long edge, at a scale of one so
 * points are pixels. Drawing applies the photo's orientation, so the result is upright.
 */
private fun UIImage.scaledJpeg(maxEdge: Int): ByteArray? {
    val (width, height) = size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, maxEdge / max(width, height))
    val targetWidth = (width * scale).roundToInt().toDouble()
    val targetHeight = (height * scale).roundToInt().toDouble()
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
    val resized = renderer.imageWithActions { _ ->
        this@scaledJpeg.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    }
    return UIImageJPEGRepresentation(resized, 0.85)?.toByteArray()
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val bytes = ByteArray(length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

/** Photos in Application Support, which is private to the app and backed up with it. */
class IosPhotoFiles : PhotoFiles {
    private val directory: String by lazy {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        "$support/photos".also {
            NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        check(bytes.toNSData().writeToFile("$directory/$name", atomically = true)) { "Couldn't save the photo" }
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.Default) {
        NSData.dataWithContentsOfFile("$directory/$name")?.toByteArray()
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.Default) {
            NSFileManager.defaultManager.removeItemAtPath("$directory/$name", error = null)
        }
    }
}

@Suppress("DEPRECATION")
private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
