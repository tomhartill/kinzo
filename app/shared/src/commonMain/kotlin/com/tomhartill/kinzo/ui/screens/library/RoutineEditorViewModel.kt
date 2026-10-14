package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Ids
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Builds one of the user's workouts. Nothing is written until [save]; closing without saving
 * leaves things as they were.
 */
class RoutineEditorViewModel(
    args: Args,
    private val routines: RoutineRepository,
    settings: SettingsRepository,
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
    private var saving = false
    private var attached = false

    val settings: StateFlow<Settings> = settings.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

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
                    // Your own gets "copy" on the end so the two are easy to tell apart.
                    draft = source.copyAsNew(name = if (source.isEditable) "${source.name} copy" else source.name)
                }
            }
            else -> {
                val empty = Routine(id = Ids.newId(), name = "", notes = null, source = RoutineSource.User, exercises = emptyList())
                original = empty
                draft = empty
            }
        }
    }

    /** The draft and what it started as, as text, for the screen to keep across process death. */
    fun savedState(): String? = draft?.let { json.encodeToString(SavedDraft.serializer(), SavedDraft(it, original)) }

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
        missing = false
    }

    fun setName(name: String) = edit { it.copy(name = name) }

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

    private fun editExercise(id: String, change: (RoutineExercise) -> RoutineExercise) = edit { routine ->
        routine.copy(exercises = routine.exercises.map { if (it.id == id) change(it) else it })
    }

    @Serializable
    private data class SavedDraft(val draft: Routine, val original: Routine?)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
