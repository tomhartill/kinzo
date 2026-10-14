package com.tomhartill.kinzo.ui.screens.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.CatalogueRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.Equipment
import com.tomhartill.kinzo.domain.ExerciseSearch
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseBrowserState(
    val loading: Boolean = true,
    /** The search text these results are for. */
    val query: String = "",
    val muscle: Muscle? = null,
    val equipment: Equipment? = null,
    val equipmentOptions: List<Equipment> = emptyList(),
    /** Recently used exercises, shown above the full list when nothing is being searched. */
    val recent: List<ExerciseSummary> = emptyList(),
    val results: List<ExerciseSummary> = emptyList(),
) {
    val isFiltering: Boolean get() = query.isNotBlank() || muscle != null || equipment != null
}

/** Search and filters over the exercise library, shared by the Exercises tab and the picker. */
class ExerciseBrowserViewModel(
    catalogue: CatalogueRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {
    /**
     * The search text. Plain Compose state, not a flow, so the text field always shows what
     * was just typed; the filtered results follow a moment later.
     */
    var query by mutableStateOf("")
        private set
    private val muscle = MutableStateFlow<Muscle?>(null)
    private val equipmentId = MutableStateFlow<String?>(null)

    private data class Library(val exercises: List<ExerciseSummary>, val recentIds: List<String>, val equipment: List<Equipment>)
    private data class Filters(val query: String, val muscle: Muscle?, val equipmentId: String?)

    val state: StateFlow<ExerciseBrowserState> = combine(
        combine(catalogue.exercises(), workouts.recentExerciseIds(), catalogue.equipment(), ::Library),
        combine(snapshotFlow { query }, muscle, equipmentId, ::Filters),
    ) { library, filters ->
        val byId = library.exercises.associateBy { it.id }
        ExerciseBrowserState(
            loading = library.exercises.isEmpty(),
            query = filters.query,
            muscle = filters.muscle,
            equipment = library.equipment.firstOrNull { it.id == filters.equipmentId },
            equipmentOptions = library.equipment,
            recent = library.recentIds.mapNotNull { byId[it] },
            results = ExerciseSearch.filter(library.exercises, filters.query, filters.muscle, filters.equipmentId),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseBrowserState())

    fun search(text: String) {
        query = text
    }

    fun setMuscle(value: Muscle?) {
        muscle.value = value
    }

    fun setEquipment(value: Equipment?) {
        equipmentId.value = value?.id
    }

    /** Adds the picked exercises, in the order they were picked, to the workout in progress. */
    fun addToActiveWorkout(exerciseIds: List<String>, onAdded: () -> Unit) {
        viewModelScope.launch {
            val workoutId = workouts.activeWorkoutId()
            if (workoutId != null && exerciseIds.isNotEmpty()) workouts.addExercises(workoutId, exerciseIds)
            onAdded()
        }
    }
}
