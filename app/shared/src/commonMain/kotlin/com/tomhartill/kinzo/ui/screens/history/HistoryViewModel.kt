package com.tomhartill.kinzo.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.data.WorkoutRepository
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.domain.WorkoutSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryState(
    val loading: Boolean = true,
    /** Finished workouts, newest first. */
    val workouts: List<WorkoutSummary> = emptyList(),
    val unit: WeightUnit = WeightUnit.Kilograms,
)

class HistoryViewModel(workouts: WorkoutRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<HistoryState> =
        combine(workouts.history(), settings.settings()) { history, settingsValue ->
            HistoryState(loading = false, workouts = history, unit = settingsValue.weightUnit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}
