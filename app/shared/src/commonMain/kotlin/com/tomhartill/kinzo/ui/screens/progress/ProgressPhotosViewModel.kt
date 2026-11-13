package com.tomhartill.kinzo.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.ProgressPhotoRepository
import com.tomhartill.kinzo.domain.Pose
import com.tomhartill.kinzo.domain.ProgressPhoto
import com.tomhartill.kinzo.photos.Camera
import com.tomhartill.kinzo.photos.PhotoSizes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Taking progress photos and looking back through them. */
class ProgressPhotosViewModel(
    private val photos: ProgressPhotoRepository,
    private val camera: Camera,
) : ViewModel() {
    /** Newest first; null until loaded. */
    val all: StateFlow<List<ProgressPhoto>?> = photos.photos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pose = MutableStateFlow(Pose.Front)
    val pose: StateFlow<Pose> = _pose.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    val hasCamera: Boolean get() = camera.hasCamera

    fun setPose(value: Pose) {
        _pose.value = value
    }

    fun take() {
        if (_busy.value) return
        _busy.value = true
        _problem.value = null
        viewModelScope.launch {
            try {
                val jpeg = camera.takePhoto(PhotoSizes.PROGRESS) ?: return@launch
                photos.add(jpeg, _pose.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _problem.value = "Couldn't save that photo. Try again."
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(photo: ProgressPhoto) {
        viewModelScope.launch { photos.delete(photo) }
    }

    suspend fun image(fileName: String): ByteArray? = photos.image(fileName)
}
