package com.tomhartill.kinzo.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.RoutineRepository
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.RoutineSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryState(
    val loading: Boolean = true,
    /** The user's own, most recently used first. */
    val mine: List<RoutineSummary> = emptyList(),
    /** Made by the AI coach (later). */
    val generated: List<RoutineSummary> = emptyList(),
    /** Bundled with the app. */
    val readyMade: List<RoutineSummary> = emptyList(),
)

/** The saved workouts in the Library tab, grouped by who made them. */
class LibraryViewModel(routines: RoutineRepository) : ViewModel() {
    val state: StateFlow<LibraryState> = routines.routines()
        .map { all ->
            val bySource = all.groupBy { it.source }
            LibraryState(
                loading = false,
                mine = bySource[RoutineSource.User].orEmpty(),
                generated = bySource[RoutineSource.Ai].orEmpty(),
                readyMade = bySource[RoutineSource.Kinzo].orEmpty(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())
}
