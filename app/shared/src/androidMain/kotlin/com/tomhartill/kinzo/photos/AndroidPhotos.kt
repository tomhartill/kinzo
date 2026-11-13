package com.tomhartill.kinzo.photos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.tomhartill.kinzo.account.ActivityTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * The phone's own camera app, through the TakePicture contract. It writes the full photo to a
 * file this app shares with it through a FileProvider (declared in the app's manifest), so no
 * camera permission is needed.
 */
class AndroidCamera(private val context: Context, private val activities: ActivityTracker) : Camera {
    private var launches = 0

    override val hasCamera: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override suspend fun takePhoto(maxEdge: Int): ByteArray? {
        val activity = activities.activity as? ComponentActivity ?: return null
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "camera").apply { mkdirs() }.resolve("capture.jpg").apply { delete() }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        val taken = withContext(Dispatchers.Main) { launchCamera(activity, uri) }
        if (!taken) return null
        return withContext(Dispatchers.Default) {
            try {
                val rotation = rotationOf(ExifInterface(file.path))
                decodeScaled(maxEdge, rotation) { options -> BitmapFactory.decodeFile(file.path, options) }
            } finally {
                file.delete()
            }
        }
    }

    private suspend fun launchCamera(activity: ComponentActivity, uri: Uri): Boolean =
        suspendCancellableCoroutine { continuation ->
            lateinit var launcher: ActivityResultLauncher<Uri>
            launcher = activity.activityResultRegistry.register("kinzo-camera-${launches++}", ActivityResultContracts.TakePicture()) { saved ->
                launcher.unregister()
                if (continuation.isActive) continuation.resume(saved)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            try {
                launcher.launch(uri)
            } catch (e: ActivityNotFoundException) {
                launcher.unregister()
                continuation.resume(false)
            }
        }

    override suspend fun resize(jpeg: ByteArray, maxEdge: Int): ByteArray = withContext(Dispatchers.Default) {
        val rotation = rotationOf(ExifInterface(ByteArrayInputStream(jpeg)))
        decodeScaled(maxEdge, rotation) { options -> BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) }
            ?: error("Couldn't read the photo")
    }
}

private fun rotationOf(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

/**
 * Decodes an image no bigger than [maxEdge] on its long edge, turned upright by [rotation]
 * degrees, as a JPEG. Decoding at a reduced sample size first keeps a 50-megapixel photo from
 * running the phone out of memory.
 */
private fun decodeScaled(maxEdge: Int, rotation: Int, decode: (BitmapFactory.Options) -> Bitmap?): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    val longEdge = max(bounds.outWidth, bounds.outHeight)
    if (longEdge <= 0) return null
    var sample = 1
    while (longEdge / (sample * 2) >= maxEdge) sample *= 2
    val sampled = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    val scale = minOf(1f, maxEdge.toFloat() / max(sampled.width, sampled.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation.toFloat())
    }
    val upright = if (scale < 1f || rotation != 0) {
        Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
    } else {
        sampled
    }
    val out = ByteArrayOutputStream()
    upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    if (upright !== sampled) upright.recycle()
    sampled.recycle()
    return out.toByteArray()
}

private const val JPEG_QUALITY = 85

/** Photos in the app's private files folder. */
class FilePhotoFiles(private val directory: File) : PhotoFiles {
    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // Written to the side first, so a crash halfway never leaves half a photo.
        val partial = directory.resolve("$name.partial")
        partial.writeBytes(bytes)
        check(partial.renameTo(directory.resolve(name))) { "Couldn't save the photo" }
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        directory.resolve(name).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { directory.resolve(name).delete() }
    }
}
