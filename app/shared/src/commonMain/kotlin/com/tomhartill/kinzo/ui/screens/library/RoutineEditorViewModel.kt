package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.MesocycleRepository
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.SplitRepository
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Ids
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.Split
import com.tomhartill.kinzo.domain.WorkoutType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Builds one of the user's workouts. Nothing is written until [save]; closing without saving
 * leaves things as they were, apart from splits added along the way.
 */
class RoutineEditorViewModel(
    args: Args,
    private val routines: RoutineRepository,
    settings: SettingsRepository,
    private val splitRepository: SplitRepository,
    private val mesocycleRepository: MesocycleRepository,
    private val clock: Clock,
) : ViewModel() {
    /** Edits [routineId], or makes a new workout, copied from [copyFromId] if given. */
    data class Args(val routineId: String? = null, val copyFromId: String? = null)

    val isNew: Boolean = args.routineId == null

    /** The workout being edited. Null while it loads. Plain Compose state, so typing is never behind. */
    var draft by mutableStateOf<Routine?>(null)
        private set

    /** True when the workout to edit is gone (deleted, or not the user's), so the screen should close. */
    var missing by mutableStateOf(false)
        private set

    // What the draft started as, to tell whether there's anything to lose. Null for a copy,
    // which is new work even before it's changed.
    private var original by mutableStateOf<Routine?>(null)

    // The name this screen filled in from the split and time of day. While the name is still
    // that (or blank), picking another split or type fills it in again.
    private var autoName by mutableStateOf<String?>(null)
    private var saving = false
    private var attached = false
    private val zone = TimeZone.currentSystemDefault()

    val settings: StateFlow<Settings> = settings.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** Every split; the screen offers the ones for the draft's type. */
    val splits: StateFlow<List<Split>> = splitRepository.splits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today, to say which mesocycle is under way. */
    fun today(): LocalDate = mesocycleRepository.today()

    /** Every mesocycle, the current one first. */
    val mesocycles: StateFlow<List<Mesocycle>> =
        mesocycleRepository.mesocycles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasChanges: Boolean get() = draft != null && draft != original

    // Loads what to edit. Cancelled if the screen hands back a draft kept from before the app
    // was killed.
    private val load = viewModelScope.launch {
        when {
            args.routineId != null -> {
                val routine = routines.routine(args.routineId).first()?.takeIf { it.isEditable }
                if (routine == null) missing = true
                original = routine
                draft = routine
            }
            args.copyFromId != null -> {
                val source = routines.routine(args.copyFromId).first()
                if (source == null) {
                    missing = true
                } else {
                    // Your own gets "copy" on the end so the two are easy to tell apart. Copies
                    // go into the current mesocycle.
                    draft = source.copyAsNew(
                        name = if (source.isEditable) "${source.name} copy" else source.name,
                        mesocycleId = mesocycleRepository.ensureCurrent().id,
                    )
                }
            }
            else -> {
                val type = WorkoutType.Resistance
                val name = suggestedName(type, split = null)
                val empty = Routine(
                    id = Ids.newId(),
                    name = name,
                    notes = null,
                    source = RoutineSource.User,
                    exercises = emptyList(),
                    type = type,
                    mesocycleId = mesocycleRepository.ensureCurrent().id,
                )
                autoName = name
                original = empty
                draft = empty
            }
        }
    }

    /** The draft and what it started as, as text, for the screen to keep across process death. */
    fun savedState(): String? = draft?.let { json.encodeToString(SavedDraft.serializer(), SavedDraft(it, original, autoName)) }

    /**
     * Called by the screen once per view model with what it kept from [savedState], if
     * anything. A view model made after the app was killed picks the unsaved draft back up; one
     * that lived on already has it and ignores this.
     */
    fun attach(saved: String?) {
        if (attached) return
        attached = true
        val restored = saved?.let { runCatching { json.decodeFromString(SavedDraft.serializer(), it) }.getOrNull() } ?: return
        load.cancel()
        original = restored.original
        draft = restored.draft
        autoName = restored.autoName
        missing = false
    }

    fun setName(name: String) = edit { it.copy(name = name) }

    /** Changes the type. A split that belongs to another type is dropped. */
    fun setType(type: WorkoutType) {
        val routine = draft ?: return
        if (routine.type == type) return
        val keepSplit = splits.value.any { it.id == routine.splitId && it.type == type }
        draft = routine.copy(
            type = type,
            splitId = routine.splitId.takeIf { keepSplit },
            splitName = routine.splitName.takeIf { keepSplit },
        )
        refreshName()
    }

    fun setSplit(split: Split?) {
        edit { it.copy(splitId = split?.id, splitName = split?.name) }
        refreshName()
    }

    /** Adds a split of the user's own for the draft's type and picks it. */
    fun addSplit(name: String) {
        val type = draft?.type ?: return
        viewModelScope.launch {
            val split = splitRepository.add(name, type) ?: return@launch
            // Only if the type hasn't changed while it was being added.
            if (draft?.type == split.type) setSplit(split)
        }
    }

    /** Removes one of the user's splits. Saved workouts filed under it keep its name. */
    fun deleteSplit(split: Split) {
        if (split.isBuiltIn) return
        if (draft?.splitId == split.id) setSplit(null)
        viewModelScope.launch { splitRepository.delete(split.id) }
    }

    fun setMesocycle(id: String) = edit { it.copy(mesocycleId = id) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes.ifEmpty { null }) }

    /** Adds exercises to the end, three sets each with no targets yet. */
    fun addExercises(exercises: List<ExerciseSummary>) = edit { routine ->
        routine.copy(
            exercises = routine.exercises + exercises.map { exercise ->
                RoutineExercise(
                    id = Ids.newId(),
                    exerciseId = exercise.id,
                    name = exercise.name,
                    measurementType = exercise.measurementType,
                    targetSets = RoutineExercise.DEFAULT_SETS,
                )
            },
        )
    }

    fun removeExercise(id: String) = edit { routine -> routine.copy(exercises = routine.exercises.filterNot { it.id == id }) }

    fun setSets(id: String, sets: Int) =
        editExercise(id) { it.copy(targetSets = sets.coerceIn(RoutineExercise.MIN_SETS, RoutineExercise.MAX_SETS)) }

    fun setTarget(id: String, target: SetValues) = editExercise(id) { it.copy(target = target.keepOnly(it.measurementType)) }

    fun setRest(id: String, seconds: Int) = editExercise(id) { it.copy(restSeconds = seconds) }

    /** Puts the exercises in this order. Unknown IDs are ignored and missing ones keep their place at the end. */
    fun reorder(orderedIds: List<String>) = edit { routine ->
        val byId = routine.exercises.associateBy { it.id }
        val ordered = orderedIds.distinct().mapNotNull { byId[it] }
        routine.copy(exercises = ordered + routine.exercises.filterNot { it in ordered })
    }

    /** Saves the draft, then calls [onSaved] with its ID. */
    fun save(onSaved: (routineId: String) -> Unit) {
        val routine = draft ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                if (routines.save(routine)) {
                    original = routine
                    onSaved(routine.id)
                }
            } finally {
                saving = false
            }
        }
    }

    private fun edit(change: (Routine) -> Routine) {
        draft = draft?.let(change)
    }

    // Names the draft after its split (or type) and the time of day, unless the user has typed
    // a name of their own.
    private fun refreshName() {
        val routine = draft ?: return
        if (routine.name.isNotBlank() && routine.name != autoName) return
        val name = suggestedName(routine.type, routine.splitName)
        autoName = name
        draft = routine.copy(name = name)
    }

    private fun suggestedName(type: WorkoutType, split: String?): String =
        type.suggestedName(split, Dates.partOfDay(clock.now().toEpochMilliseconds(), zone))

    private fun editExercise(id: String, change: (RoutineExercise) -> RoutineExercise) = edit { routine ->
        routine.copy(exercises = routine.exercises.map { if (it.id == id) change(it) else it })
    }

    @Serializable
    private data class SavedDraft(val draft: Routine, val original: Routine?, val autoName: String? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
