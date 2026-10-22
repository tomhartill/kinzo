package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.Pose
import com.tomhartill.kinzo.photos.Camera
import com.tomhartill.kinzo.photos.PhotoFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressPhotoRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val files = MemoryPhotoFiles()
    private val repository = ProgressPhotoRepository(db, files, FakeCamera(), clock, Dispatchers.Unconfined)

    @Test
    fun savesThePhotoAndAThumbnailNewestFirst() = runTest {
        val first = repository.add(byteArrayOf(1, 2, 3), Pose.Front)
        clock.advance(7 * 24 * 60 * 60 * 1000L)
        val second = repository.add(byteArrayOf(4, 5, 6), Pose.Side)

        assertEquals(listOf(second, first), repository.photos().first())
        assertEquals(Pose.Side, second.pose)
        assertEquals(listOf<Byte>(1, 2, 3), repository.image(first.fileName)?.toList())
        // The fake camera "shrinks" a photo to its first byte.
        assertEquals(listOf<Byte>(1), repository.image(first.thumbnailFileName)?.toList())
    }

    @Test
    fun deletingRemovesTheFilesAndHidesThePhoto() = runTest {
        val photo = repository.add(byteArrayOf(1, 2, 3), Pose.Back)

        repository.delete(photo)

        assertEquals(emptyList(), repository.photos().first())
        assertTrue(files.names.isEmpty())
    }

    private class FakeCamera : Camera {
        override val hasCamera = true

        override suspend fun takePhoto(maxEdge: Int): ByteArray? = null

        override suspend fun resize(jpeg: ByteArray, maxEdge: Int): ByteArray = jpeg.copyOf(1)
    }

    private class MemoryPhotoFiles : PhotoFiles {
        private val stored = mutableMapOf<String, ByteArray>()
        val names: Set<String> get() = stored.keys

        override suspend fun write(name: String, bytes: ByteArray) {
            stored[name] = bytes
        }

        override suspend fun read(name: String): ByteArray? = stored[name]

        override suspend fun delete(name: String) {
            stored.remove(name)
        }
    }
}
