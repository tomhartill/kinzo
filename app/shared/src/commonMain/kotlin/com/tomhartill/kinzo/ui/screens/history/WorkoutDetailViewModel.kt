package com.tomhartill.kinzo.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.Workout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutDetailState(
    val loading: Boolean = true,
    val workout: Workout? = null,
    val unit: WeightUnit = WeightUnit.Kilograms,
)

class WorkoutDetailViewModel(
    private val workoutId: String,
    private val workouts: WorkoutRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<WorkoutDetailState> =
        combine(workouts.workout(workoutId), settings.settings()) { workout, settingsValue ->
            WorkoutDetailState(loading = false, workout = workout, unit = settingsValue.weightUnit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workouts.deleteWorkout(workoutId)
            onDeleted()
        }
    }
}
