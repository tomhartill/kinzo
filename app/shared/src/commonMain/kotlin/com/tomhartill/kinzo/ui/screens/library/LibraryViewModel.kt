package com.tomhartill.kinzo.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.MesocycleRepository
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.RoutineSummary
import com.tomhartill.kinzo.domain.currentOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

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
    /** Every mesocycle, the latest start first. */
    val mesocycles: List<MesocycleWorkouts> = emptyList(),
    /** The one under way today (see [currentOn]). */
    val current: Mesocycle? = null,
    val today: LocalDate? = null,
) {
    /** The one with the latest start, which the next has to start after. */
    val latest: MesocycleWorkouts? get() = mesocycles.firstOrNull()

    private val currentIndex: Int get() = mesocycles.indexOfFirst { it.mesocycle == current }

    /** Planned to start after the current one, the latest start first. */
    val upcoming: List<MesocycleWorkouts> get() = if (currentIndex < 0) emptyList() else mesocycles.take(currentIndex)

    /** Started before the current one, the latest start first. */
    val earlier: List<MesocycleWorkouts> get() = if (currentIndex < 0) emptyList() else mesocycles.drop(currentIndex + 1)

    /** The one that takes over from the current one, if it's planned already. */
    val upNext: MesocycleWorkouts? get() = upcoming.lastOrNull()
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
        val today = mesocycles.today()
        val current = blocks.currentOn(today)
        LibraryState(
            loading = false,
            // Before the first mesocycle exists, everything of the user's own is current.
            mine = own.filter { current == null || it.mesocycleId == null || it.mesocycleId == current.id },
            generated = bySource[RoutineSource.Ai].orEmpty(),
            readyMade = bySource[RoutineSource.Kinzo].orEmpty(),
            mesocycles = blocks.map { block -> MesocycleWorkouts(block, own.filter { it.mesocycleId == block.id }) },
            current = current,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    /** What the form for the next mesocycle starts with: the next name, and dates from today. */
    fun nextDraft(): MesocycleDraft {
        val start = Mesocycle.nextStart(state.value.latest?.mesocycle, mesocycles.today())
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
