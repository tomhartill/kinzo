package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.ThemeName
import com.tomhartill.kinzo.domain.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {
    private val settings = SettingsRepository(inMemoryDatabase(), Dispatchers.Unconfined)

    @Test
    fun defaultsUntilChanged() = runTest {
        assertEquals(Settings(), settings.settings().first())
        settings.setWeightUnit(WeightUnit.Pounds)
        settings.setRestSeconds(120)
        assertEquals(Settings(weightUnit = WeightUnit.Pounds, restSeconds = 120), settings.settings().first())
    }

    @Test
    fun keepsTheChosenThemeModeAndTips() = runTest {
        settings.setTheme(ThemeName.Sage)
        settings.setThemeMode(ThemeMode.Dark)
        settings.setTipsEnabled(false)
        val read = settings.settings().first()
        assertEquals(ThemeName.Sage, read.theme)
        assertEquals(ThemeMode.Dark, read.themeMode)
        assertEquals(false, read.tipsEnabled)
    }
}
