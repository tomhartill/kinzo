package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.Settings
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
}
