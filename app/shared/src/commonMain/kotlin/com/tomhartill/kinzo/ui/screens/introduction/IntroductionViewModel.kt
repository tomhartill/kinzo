package com.tomhartill.kinzo.ui.screens.introduction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.ThemeName
import com.tomhartill.kinzo.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The introduction's choices write straight to settings, so each one applies as it's made. */
class IntroductionViewModel(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<Settings?> =
        settings.settings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    fun setRestSeconds(seconds: Int) {
        viewModelScope.launch { settings.setRestSeconds(seconds) }
    }

    fun setTheme(theme: ThemeName) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}
