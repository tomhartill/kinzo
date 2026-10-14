package com.tomhartill.kinzo.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.MesocycleRepository
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.RoutineSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A mesocycle and the user's workouts in it. */
data class MesocycleWorkouts(val mesocycle: Mesocycle, val workouts: List<RoutineSummary>)

data class LibraryState(
    val loading: Boolean = true,
    /** The user's own in the current mesocycle, most recently used first. */
    val mine: List<RoutineSummary> = emptyList(),
    /** Made by the AI coach (later). */
    val generated: List<RoutineSummary> = emptyList(),
    /** Bundled with the app. */
    val readyMade: List<RoutineSummary> = emptyList(),
    /** Every mesocycle, the current one first. */
    val mesocycles: List<MesocycleWorkouts> = emptyList(),
    val today: LocalDate? = null,
) {
    val current: Mesocycle? get() = mesocycles.firstOrNull()?.mesocycle
}

/** The saved workouts in the Library tab, grouped by who made them, and the mesocycles. */
class LibraryViewModel(
    routines: RoutineRepository,
    private val mesocycles: MesocycleRepository,
) : ViewModel() {
    private var starting = false

    val state: StateFlow<LibraryState> = combine(routines.routines(), mesocycles.mesocycles()) { all, blocks ->
        val bySource = all.groupBy { it.source }
        val own = bySource[RoutineSource.User].orEmpty()
        val current = blocks.firstOrNull()
        LibraryState(
            loading = false,
            // Before the first mesocycle exists, everything of the user's own is current.
            mine = own.filter { current == null || it.mesocycleId == null || it.mesocycleId == current.id },
            generated = bySource[RoutineSource.Ai].orEmpty(),
            readyMade = bySource[RoutineSource.Kinzo].orEmpty(),
            mesocycles = blocks.map { block -> MesocycleWorkouts(block, own.filter { it.mesocycleId == block.id }) },
            today = mesocycles.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    /** What the form for the next mesocycle starts with: the next name, and dates from today. */
    fun nextDraft(): MesocycleDraft {
        val current = state.value.current
        // It has to start after the current one does, so a block begun today makes it tomorrow.
        val start = mesocycles.today().let { today ->
            if (current != null && today <= current.start) current.start.plus(1, DateTimeUnit.DAY) else today
        }
        return MesocycleDraft(
            name = "Mesocycle ${state.value.mesocycles.size + 1}",
            start = start,
            end = mesocycles.defaultEnd(start),
        )
    }

    /** The end date that goes with a new start date, unless the user picks one. */
    fun defaultEnd(start: LocalDate): LocalDate = mesocycles.defaultEnd(start)

    /** Starts the next mesocycle, then calls [onStarted] with it. */
    fun startNext(draft: MesocycleDraft, copyWorkouts: Boolean, onStarted: (Mesocycle) -> Unit) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                mesocycles.startNext(draft.name, draft.start, draft.end, copyWorkouts)?.let(onStarted)
            } finally {
                starting = false
            }
        }
    }
}

/** A mesocycle's name and dates while they're being edited. */
data class MesocycleDraft(val name: String, val start: LocalDate, val end: LocalDate)
