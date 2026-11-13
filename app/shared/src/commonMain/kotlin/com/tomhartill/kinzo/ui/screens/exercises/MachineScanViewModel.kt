package com.tomhartill.kinzo.ui.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.AccountRepository
import com.tomhartill.kinzo.data.CatalogueRepository
import com.tomhartill.kinzo.data.api.ApiException
import com.tomhartill.kinzo.data.api.KinzoApi
import com.tomhartill.kinzo.data.api.MachineScan
import com.tomhartill.kinzo.domain.Machine
import com.tomhartill.kinzo.photos.Camera
import com.tomhartill.kinzo.photos.PhotoSizes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One machine a scan thinks the photo shows. */
data class ScannedMachine(
    val machine: Machine,
    /** "high", "medium" or "low". */
    val confidence: String,
)

sealed interface MachineScanState {
    data object Idle : MachineScanState

    /** Waiting on the camera, then on the server. */
    data class Scanning(val uploading: Boolean) : MachineScanState

    /**
     * Best match first. [suggestedExerciseId] is the variation the photo most likely shows,
     * from the first match. [matches] is empty when Kinzo doesn't know the machine, and then
     * [description] is all there is.
     */
    data class Found(
        val matches: List<ScannedMachine>,
        val suggestedExerciseId: String?,
        val description: String,
    ) : MachineScanState

    /** [signIn] when the fix is to sign in. */
    data class Failed(val message: String, val signIn: Boolean = false) : MachineScanState
}

/**
 * Point the camera at a machine and get back what it is and which variation it is. The photo
 * goes to the Kinzo API, which asks a vision model and answers with machine IDs from the
 * catalogue this phone already has.
 */
class MachineScanViewModel(
    private val camera: Camera,
    private val api: KinzoApi,
    private val accounts: AccountRepository,
    private val catalogue: CatalogueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<MachineScanState>(MachineScanState.Idle)
    val state: StateFlow<MachineScanState> = _state.asStateFlow()

    private var job: Job? = null

    fun scan() {
        if (job?.isActive == true) return
        if (accounts.account.value == null) {
            _state.value = MachineScanState.Failed("Sign in to scan machines. It's free, and everything else works without it.", signIn = true)
            return
        }
        job = viewModelScope.launch {
            _state.value = MachineScanState.Scanning(uploading = false)
            val photo = try {
                camera.takePhoto(PhotoSizes.SCAN)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (photo == null) {
                _state.value = MachineScanState.Idle
                return@launch
            }
            _state.value = MachineScanState.Scanning(uploading = true)
            _state.value = try {
                found(api.scanMachine(photo))
            } catch (e: ApiException) {
                MachineScanState.Failed(e.message, signIn = e.code == ApiException.SIGNED_OUT)
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = MachineScanState.Idle
    }

    private suspend fun found(scan: MachineScan): MachineScanState.Found {
        val machines = catalogue.machines().associateBy { it.id }
        // An app with an older catalogue than the server's skips machines it doesn't have yet.
        val matches = scan.matches.mapNotNull { match ->
            machines[match.machineId]?.let { ScannedMachine(it, match.confidence) }
        }
        return MachineScanState.Found(
            matches = matches,
            suggestedExerciseId = scan.exerciseId?.takeIf { id -> matches.firstOrNull()?.machine?.exerciseIds?.contains(id) == true },
            description = scan.description,
        )
    }
}
