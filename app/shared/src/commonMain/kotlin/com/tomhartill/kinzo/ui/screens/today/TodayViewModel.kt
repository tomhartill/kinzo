package com.tomhartill.kinzo.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.MesocycleRepository
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.RoutineSummary
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.WorkoutSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

data class TodayState(
    val loading: Boolean = true,
    val hasActiveWorkout: Boolean = false,
    val lastWorkout: WorkoutSummary? = null,
    val weekWorkouts: Int = 0,
    val weekVolumeKg: Double = 0.0,
    val unit: WeightUnit = WeightUnit.Kilograms,
    /**
     * The user's most recently used workouts in the current mesocycle, or a few ready-made ones
     * until they have some.
     */
    val routines: List<RoutineSummary> = emptyList(),
) {
    val hasOwnRoutines: Boolean get() = routines.any { it.source == RoutineSource.User }
}

private const val ROUTINES_SHOWN = 3

class TodayViewModel(
    private val workouts: WorkoutRepository,
    routines: RoutineRepository,
    settings: SettingsRepository,
    mesocycles: MesocycleRepository,
    private val clock: Clock,
) : ViewModel() {
    private val zone = TimeZone.currentSystemDefault()
    private var starting = false

    val state: StateFlow<TodayState> = combine(
        workouts.activeWorkoutInfo().map { it != null }.distinctUntilChanged(),
        workouts.history(),
        settings.settings(),
        routines.routines(),
        mesocycles.current(),
    ) { hasActive, history, settingsValue, saved, mesocycle ->
        val weekStart = Dates.startOfWeek(clock.now().toEpochMilliseconds(), zone)
        val thisWeek = history.filter { it.startedAt >= weekStart }
        TodayState(
            loading = false,
            hasActiveWorkout = hasActive,
            lastWorkout = history.firstOrNull(),
            weekWorkouts = thisWeek.size,
            weekVolumeKg = thisWeek.sumOf { it.volumeKg },
            unit = settingsValue.weightUnit,
            routines = saved
                .filter { it.source == RoutineSource.User && (mesocycle == null || it.mesocycleId in setOf(null, mesocycle.id)) }
                .ifEmpty { saved.filter { it.source == RoutineSource.Kinzo } }
                .take(ROUTINES_SHOWN),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** Starts an empty workout named for the time of day, or reopens the one in progress. */
    fun startWorkout(onStarted: () -> Unit) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                workouts.startWorkout(Dates.defaultWorkoutName(clock.now().toEpochMilliseconds(), zone))
                onStarted()
            } finally {
                starting = false
            }
        }
    }
}
