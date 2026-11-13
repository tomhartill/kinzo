package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.Ids
import com.tomhartill.kinzo.domain.Pose
import com.tomhartill.kinzo.domain.ProgressPhoto
import com.tomhartill.kinzo.photos.Camera
import com.tomhartill.kinzo.photos.PhotoFiles
import com.tomhartill.kinzo.photos.PhotoSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Progress photos. The images live in the app's private storage ([PhotoFiles]), never the
 * phone's gallery, and the database only names their files.
 */
class ProgressPhotoRepository(
    private val db: KinzoDatabase,
    private val files: PhotoFiles,
    private val camera: Camera,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.progressPhotoQueries

    /** Newest first. */
    fun photos(): Flow<List<ProgressPhoto>> = queries.photos().asFlow().mapToList(io)
        .map { rows -> rows.map { ProgressPhoto(it.id, it.taken_at, Pose.fromKey(it.pose), it.file_name, it.thumbnail_file_name) } }
        .distinctUntilChanged()

    /** Saves a photo the camera took, with a thumbnail for the grid. */
    suspend fun add(jpeg: ByteArray, pose: Pose): ProgressPhoto {
        val id = Ids.newId()
        val now = clock.now().toEpochMilliseconds()
        val photo = ProgressPhoto(id, now, pose, "$id.jpg", "$id-thumb.jpg")
        val thumbnail = camera.resize(jpeg, PhotoSizes.THUMBNAIL)
        files.write(photo.fileName, jpeg)
        files.write(photo.thumbnailFileName, thumbnail)
        withContext(io) {
            queries.insertPhoto(photo.id, photo.takenAt, pose.key, photo.fileName, photo.thumbnailFileName, now)
        }
        return photo
    }

    suspend fun image(fileName: String): ByteArray? = files.read(fileName)

    /** Deletes the photo's files straight away; the row stays, marked deleted, for sync. */
    suspend fun delete(photo: ProgressPhoto) {
        withContext(io) { queries.softDeletePhoto(clock.now().toEpochMilliseconds(), photo.id) }
        files.delete(photo.fileName)
        files.delete(photo.thumbnailFileName)
    }
}
