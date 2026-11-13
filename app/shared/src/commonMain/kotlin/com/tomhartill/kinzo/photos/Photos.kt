package com.tomhartill.kinzo.photos

/**
 * The platform's camera. Where there is none (desktop, the iOS simulator) [takePhoto] picks a
 * photo from the computer or the photo library instead.
 */
interface Camera {
    /** True when [takePhoto] opens a real camera rather than a file or photo picker. */
    val hasCamera: Boolean

    /**
     * Takes a photo and returns it as an upright JPEG no bigger than [maxEdge] pixels on its
     * long edge, or null if the person backed out.
     */
    suspend fun takePhoto(maxEdge: Int): ByteArray?

    /** Shrinks a JPEG to [maxEdge] pixels on its long edge, for thumbnails. */
    suspend fun resize(jpeg: ByteArray, maxEdge: Int): ByteArray
}

/**
 * Photo files kept in the app's private storage, by file name. Only the app can read them, and
 * they go when the app is removed.
 */
interface PhotoFiles {
    suspend fun write(name: String, bytes: ByteArray)

    /** Null if the file is missing. */
    suspend fun read(name: String): ByteArray?

    suspend fun delete(name: String)
}

object PhotoSizes {
    /** Enough for the vision model to read a placard, small enough to upload quickly. */
    const val SCAN = 1280

    /** Big enough to compare week to week, about half a megabyte a photo. */
    const val PROGRESS = 2048

    const val THUMBNAIL = 360
}
