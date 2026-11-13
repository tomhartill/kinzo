package com.tomhartill.kinzo.photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/** The desktop app has no camera, so it opens a JPEG or PNG from the computer instead. */
class DesktopCamera : Camera {
    override val hasCamera: Boolean = false

    override suspend fun takePhoto(maxEdge: Int): ByteArray? {
        val file = pickFile() ?: return null
        return withContext(Dispatchers.IO) {
            val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return@withContext null
            encodeJpeg(scaled(image, maxEdge))
        }
    }

    override suspend fun resize(jpeg: ByteArray, maxEdge: Int): ByteArray = withContext(Dispatchers.IO) {
        val image = ImageIO.read(ByteArrayInputStream(jpeg)) ?: error("Couldn't read the photo")
        encodeJpeg(scaled(image, maxEdge))
    }

    private suspend fun pickFile(): File? = suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            val dialog = FileDialog(null as Frame?, "Choose a photo", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png") }
                isVisible = true
            }
            val file = dialog.file?.let { File(dialog.directory, it) }
            if (continuation.isActive) continuation.resume(file)
        }
    }
}

private fun scaled(image: BufferedImage, maxEdge: Int): BufferedImage {
    val scale = minOf(1.0, maxEdge.toDouble() / max(image.width, image.height))
    val width = max(1, (image.width * scale).roundToInt())
    val height = max(1, (image.height * scale).roundToInt())
    // JPEG has no alpha, so draw onto an RGB image whatever the source was.
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = out.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.drawImage(image, 0, 0, width, height, null)
    graphics.dispose()
    return out
}

private fun encodeJpeg(image: BufferedImage): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.85f
        }
        writer.write(null, IIOImage(image, null, null), params)
        writer.dispose()
    }
    return out.toByteArray()
}

/** Photos in a folder on the computer, ~/.kinzo/photos by default. */
class FilePhotoFiles(
    private val directory: File = File(System.getProperty("user.home"), ".kinzo/photos"),
) : PhotoFiles {
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
