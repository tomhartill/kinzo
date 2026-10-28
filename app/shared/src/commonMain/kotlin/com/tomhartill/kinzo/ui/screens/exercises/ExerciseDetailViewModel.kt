package com.tomhartill.kinzo.ui.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.CatalogueRepository
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.ExerciseDetail
import com.tomhartill.kinzo.domain.ExerciseSession
import com.tomhartill.kinzo.domain.ExerciseStats
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseDetailState(
    val loading: Boolean = true,
    val exercise: ExerciseDetail? = null,
    /** Finished workouts with this exercise, newest first. */
    val sessions: List<ExerciseSession> = emptyList(),
    val best: SetValues? = null,
    val unit: WeightUnit = WeightUnit.Kilograms,
)

class ExerciseDetailViewModel(
    private val exerciseId: String,
    catalogue: CatalogueRepository,
    private val workouts: WorkoutRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ExerciseDetailState> = combine(
        catalogue.exercise(exerciseId),
        workouts.exerciseHistory(exerciseId),
        settings.settings(),
    ) { exercise, sessions, settingsValue ->
        ExerciseDetailState(
            loading = false,
            exercise = exercise,
            sessions = sessions,
            best = exercise?.let { ExerciseStats.bestSet(it.measurementType, sessions.flatMap { session -> session.sets }) },
            unit = settingsValue.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())

    /** Deletes this exercise from one past workout, leaving the rest of that workout alone. */
    fun deleteSession(workoutId: String) {
        viewModelScope.launch { workouts.removeExerciseFromWorkout(workoutId, exerciseId) }
    }
}
