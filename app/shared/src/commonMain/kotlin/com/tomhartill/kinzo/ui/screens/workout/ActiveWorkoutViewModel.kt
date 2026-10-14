package com.tomhartill.kinzo.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.LoggedSet
import com.tomhartill.kinzo.domain.RestTimer
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.Workout
import com.tomhartill.kinzo.domain.WorkoutExercise
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveWorkoutState(
    val loading: Boolean = true,
    val workout: Workout? = null,
    val settings: Settings = Settings(),
)

class ActiveWorkoutViewModel(
    private val workouts: WorkoutRepository,
    settings: SettingsRepository,
    private val restTimer: RestTimer,
) : ViewModel() {
    val state: StateFlow<ActiveWorkoutState> =
        combine(workouts.activeWorkout(), settings.settings()) { workout, settingsValue ->
            ActiveWorkoutState(loading = false, workout = workout, settings = settingsValue)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutState())

    val rest: StateFlow<RestTimer.State?> = restTimer.state

    private val workoutId: String? get() = state.value.workout?.id

    fun rename(name: String) {
        val id = workoutId ?: return
        viewModelScope.launch { workouts.renameWorkout(id, name) }
    }

    fun addSet(exercise: WorkoutExercise) {
        viewModelScope.launch { workouts.addSet(exercise.id) }
    }

    /**
     * Saves what was typed. A ticked set must stay complete: blanks take last time's numbers
     * as when ticking, and an edit that would leave it incomplete (reps cleared with nothing
     * to fall back on) isn't saved, so the field goes back to the saved number when left.
     */
    fun updateSet(exercise: WorkoutExercise, set: LoggedSet, values: SetValues) {
        val toSave = if (set.isCompleted) {
            val index = exercise.sets.indexOfFirst { it.id == set.id }
            SetRules.resolveForCompletion(exercise.measurementType, values, exercise.previous.getOrNull(index)?.values) ?: return
        } else {
            values
        }
        viewModelScope.launch { workouts.updateSet(set.id, toSave) }
    }

    /**
     * Ticks or unticks a set. Ticking fills blanks from last time and starts the rest timer.
     * Returns false, changing nothing, when the set is missing a number it needs.
     */
    fun toggleDone(exercise: WorkoutExercise, set: LoggedSet, entered: SetValues): Boolean {
        if (set.isCompleted) {
            viewModelScope.launch { workouts.uncompleteSet(set.id) }
            return true
        }
        val index = exercise.sets.indexOfFirst { it.id == set.id }
        val previous = exercise.previous.getOrNull(index)?.values
        val values = SetRules.resolveForCompletion(exercise.measurementType, entered, previous) ?: return false
        viewModelScope.launch { workouts.completeSet(set.id, values) }
        restTimer.start(restFor(exercise))
        return true
    }

    /** The rest after each set of [exercise], in seconds; 0 when its timer is off. */
    fun restFor(exercise: WorkoutExercise): Int = exercise.rest(state.value.settings.restSeconds)

    /** Sets the rest after each set of one exercise, 0 for none. It's remembered for next time. */
    fun setRest(exercise: WorkoutExercise, seconds: Int) {
        viewModelScope.launch { workouts.setExerciseRest(exercise.id, seconds) }
    }

    /** Starts a rest by hand, between exercises for example. */
    fun startRest(seconds: Int) = restTimer.start(seconds)

    fun toggleWarmup(set: LoggedSet) {
        viewModelScope.launch { workouts.setWarmup(set.id, !set.isWarmup) }
    }

    fun removeSet(set: LoggedSet) {
        viewModelScope.launch { workouts.removeSet(set.id) }
    }

    /** Undoes [removeSet]. */
    fun restoreSet(set: LoggedSet) {
        viewModelScope.launch { workouts.restoreSet(set.id) }
    }

    fun removeExercise(exercise: WorkoutExercise) {
        viewModelScope.launch { workouts.removeExercise(exercise.id) }
    }

    /** Saves a new order for the exercises, given as their IDs. */
    fun reorderExercises(orderedIds: List<String>) {
        val id = workoutId ?: return
        viewModelScope.launch { workouts.reorderExercises(id, orderedIds) }
    }

    fun adjustRest(seconds: Int) = restTimer.adjust(seconds)

    fun skipRest() = restTimer.skip()

    /** Finishes the workout and reports its ID, or does nothing if no set was completed. */
    fun finish(onFinished: (workoutId: String) -> Unit) {
        val id = workoutId ?: return
        viewModelScope.launch {
            if (workouts.finishWorkout(id)) {
                restTimer.skip()
                onFinished(id)
            }
        }
    }

    fun discard(onDiscarded: () -> Unit) {
        val id = workoutId ?: return
        viewModelScope.launch {
            workouts.deleteWorkout(id)
            restTimer.skip()
            onDiscarded()
        }
    }
}
