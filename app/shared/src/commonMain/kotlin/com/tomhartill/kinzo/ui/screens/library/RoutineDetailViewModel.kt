package com.tomhartill.kinzo.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.data.WorkoutRepository.StartResult
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineDetailState(
    val loading: Boolean = true,
    /** Null once loaded means it was deleted. */
    val routine: Routine? = null,
    val settings: Settings = Settings(),
)

class RoutineDetailViewModel(
    private val routineId: String,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private var busy = false

    val state: StateFlow<RoutineDetailState> = combine(routines.routine(routineId), settings.settings()) { routine, settingsValue ->
        RoutineDetailState(loading = false, routine = routine, settings = settingsValue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailState())

    /**
     * Starts a workout from this plan. [onStarted] opens it; [onBusy] says another workout is
     * already in progress and must be finished or discarded first.
     */
    fun start(onStarted: () -> Unit, onBusy: () -> Unit) = once {
        when (workouts.startFromRoutine(routineId)) {
            is StartResult.Started -> onStarted()
            is StartResult.AlreadyInProgress -> onBusy()
            StartResult.NotFound -> Unit
        }
    }

    /** Deletes it (the user's own only). The screen closes when the routine disappears. */
    fun delete() = once { routines.delete(routineId) }

    // Ignores a second tap while the first is still being handled.
    private fun once(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                action()
            } finally {
                busy = false
            }
        }
    }
}
