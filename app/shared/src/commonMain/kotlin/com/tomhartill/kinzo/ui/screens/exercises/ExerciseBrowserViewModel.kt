package com.tomhartill.kinzo.ui.screens.exercises

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.CatalogueRepository
import com.tomhartill.kinzo.data.TrendRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.Equipment
import com.tomhartill.kinzo.domain.ExerciseGroups
import com.tomhartill.kinzo.domain.ExerciseListRow
import com.tomhartill.kinzo.domain.ExerciseSearch
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Muscle
import com.tomhartill.kinzo.domain.Trend
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
    /**
     * [results] with variations folded into their groups, such as every bench press under
     * Bench press. Used when nothing is typed; a search lists matching variations directly.
     */
    val rows: List<ExerciseListRow> = emptyList(),
    /** Every exercise, to look up the ones picked whatever the current search shows. */
    val byId: Map<String, ExerciseSummary> = emptyMap(),
    /** Recent progress by exercise ID, for the sparkline on each row. Read from a small cache. */
    val trends: Map<String, Trend> = emptyMap(),
) {
    val isFiltering: Boolean get() = query.isNotBlank() || muscle != null || equipment != null
}

/** Search and filters over the exercise library, shared by the Library tab and the pickers. */
class ExerciseBrowserViewModel(
    catalogue: CatalogueRepository,
    private val workouts: WorkoutRepository,
    trends: TrendRepository,
) : ViewModel() {
    /**
     * The search text. Plain Compose state, not a flow, so the text field always shows what
     * was just typed; the filtered results follow a moment later.
     */
    var query by mutableStateOf("")
        private set
    private val muscle = MutableStateFlow<Muscle?>(null)
    private val equipmentId = MutableStateFlow<String?>(null)

    private data class Library(
        val exercises: List<ExerciseSummary>,
        val recentIds: List<String>,
        val equipment: List<Equipment>,
        val trends: Map<String, Trend>,
    )
    private data class Filters(val query: String, val muscle: Muscle?, val equipmentId: String?)

    val state: StateFlow<ExerciseBrowserState> = combine(
        combine(catalogue.exercises(), workouts.recentExerciseIds(), catalogue.equipment(), trends.exerciseTrends(), ::Library),
        combine(snapshotFlow { query }, muscle, equipmentId, ::Filters),
    ) { library, filters ->
        val byId = library.exercises.associateBy { it.id }
        val results = ExerciseSearch.filter(library.exercises, filters.query, filters.muscle, filters.equipmentId)
        ExerciseBrowserState(
            loading = library.exercises.isEmpty(),
            query = filters.query,
            muscle = filters.muscle,
            equipment = library.equipment.firstOrNull { it.id == filters.equipmentId },
            equipmentOptions = library.equipment,
            recent = library.recentIds.mapNotNull { byId[it] },
            results = results,
            rows = if (filters.query.isBlank()) ExerciseGroups.rows(results) else emptyList(),
            byId = byId,
            trends = library.trends,
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

    /**
     * Adds the picked exercises, in the order they were picked, to [workoutId], or to the
     * workout in progress when that's null.
     */
    fun addToWorkout(workoutId: String?, exerciseIds: List<String>, onAdded: () -> Unit) {
        viewModelScope.launch {
            val target = workoutId ?: workouts.activeWorkoutId()
            if (target != null && exerciseIds.isNotEmpty()) workouts.addExercises(target, exerciseIds)
            onAdded()
        }
    }

    /** Swaps the exercise [workoutExerciseId] in a workout for [exerciseId], in the same place. */
    fun replaceInWorkout(workoutExerciseId: String, exerciseId: String, onReplaced: () -> Unit) {
        viewModelScope.launch {
            workouts.replaceExercise(workoutExerciseId, exerciseId)
            onReplaced()
        }
    }
}
