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
import kotlinx.datetime.LocalDate

data class MesocycleState(
    val loading: Boolean = true,
    /** Null once loaded means it's gone. */
    val mesocycle: Mesocycle? = null,
    val isCurrent: Boolean = false,
    /** The user's workouts in it, most recently used first. */
    val workouts: List<RoutineSummary> = emptyList(),
    val today: LocalDate? = null,
)

class MesocycleViewModel(
    private val mesocycleId: String,
    routines: RoutineRepository,
    private val mesocycles: MesocycleRepository,
) : ViewModel() {
    val state: StateFlow<MesocycleState> = combine(mesocycles.mesocycles(), routines.routines()) { blocks, all ->
        MesocycleState(
            loading = false,
            mesocycle = blocks.firstOrNull { it.id == mesocycleId },
            isCurrent = blocks.firstOrNull()?.id == mesocycleId,
            workouts = all.filter { it.source == RoutineSource.User && it.mesocycleId == mesocycleId },
            today = mesocycles.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MesocycleState())

    fun defaultEnd(start: LocalDate): LocalDate = mesocycles.defaultEnd(start)

    fun update(draft: MesocycleDraft) {
        viewModelScope.launch { mesocycles.update(mesocycleId, draft.name, draft.start, draft.end) }
    }
}
