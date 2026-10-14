package com.tomhartill.kinzo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.CatalogueImporter
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.ActiveWorkoutInfo
import com.tomhartill.kinzo.domain.RestTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state: the workout in progress for the banner over the tabs, and the rest timer. */
class AppViewModel(
    workouts: WorkoutRepository,
    restTimer: RestTimer,
    importer: CatalogueImporter,
) : ViewModel() {
    val activeWorkout: StateFlow<ActiveWorkoutInfo?> =
        workouts.activeWorkoutInfo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rest: StateFlow<RestTimer.State?> = restTimer.state

    /** Fires when a rest countdown runs out, wherever the user is in the app. */
    val restFinished: SharedFlow<Unit> = restTimer.finished

    init {
        // Fills or updates the exercise library on first launch and after app updates. If it
        // fails (a full disk, say) the last imported library stays and the next launch retries;
        // the transaction means nothing is half-written.
        viewModelScope.launch {
            try {
                importer.importIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Kinzo: exercise library import failed: $e")
            }
        }
    }
}
