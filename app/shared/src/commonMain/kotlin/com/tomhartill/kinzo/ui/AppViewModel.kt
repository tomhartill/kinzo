package com.tomhartill.kinzo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.CatalogueImporter
import com.tomhartill.kinzo.data.KinzoWorkoutsImporter
import com.tomhartill.kinzo.data.MesocycleRepository
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
    catalogueImporter: CatalogueImporter,
    workoutsImporter: KinzoWorkoutsImporter,
    mesocycles: MesocycleRepository,
) : ViewModel() {
    val activeWorkout: StateFlow<ActiveWorkoutInfo?> =
        workouts.activeWorkoutInfo().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rest: StateFlow<RestTimer.State?> = restTimer.state

    /** Fires when a rest countdown runs out, wherever the user is in the app. */
    val restFinished: SharedFlow<Unit> = restTimer.finished

    init {
        // Fills or updates the exercise library, then the ready-made workouts that use it, on
        // first launch and after app updates. If either fails (a full disk, say) the last
        // imported version stays and the next launch retries; each runs in a transaction, so
        // nothing is half-written.
        viewModelScope.launch {
            runSafely("exercise library import") { catalogueImporter.importIfNeeded() }
            runSafely("ready-made workouts import") { workoutsImporter.importIfNeeded() }
        }
        // The first mesocycle starts the first time this version runs, and takes in the
        // workouts saved before mesocycles existed.
        viewModelScope.launch {
            runSafely("starting the first mesocycle") { mesocycles.ensureCurrent() }
        }
    }

    private suspend fun runSafely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Kinzo: $what failed: $e")
        }
    }
}
