package com.tomhartill.kinzo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GuidanceRepositoryTest {
    private val db = inMemoryDatabase()
    private val guidance = GuidanceRepository(db, Dispatchers.Unconfined)
    private val settings = SettingsRepository(db, Dispatchers.Unconfined)

    @Test
    fun remembersSeenTipsUntilReset() = runTest {
        guidance.markTipSeen("today.intro")
        guidance.markTipSeen("workout.log-set")
        settings.setWeightUnit(com.tomhartill.kinzo.domain.WeightUnit.Pounds)
        assertEquals(setOf("today.intro", "workout.log-set"), guidance.tips().first().seen)
        guidance.resetTips()
        assertEquals(emptySet(), guidance.tips().first().seen)
        // Resetting tips leaves other settings alone.
        assertEquals(com.tomhartill.kinzo.domain.WeightUnit.Pounds, settings.settings().first().weightUnit)
    }

    @Test
    fun tipsCanBeSwitchedOff() = runTest {
        assertEquals(true, guidance.tips().first().enabled)
        settings.setTipsEnabled(false)
        assertEquals(false, guidance.tips().first().enabled)
    }

    @Test
    fun aFreshInstallHasntSeenTheIntroduction() = runTest {
        assertEquals(0, guidance.introductionVersion())
        guidance.setIntroductionVersion(1)
        assertEquals(1, guidance.introductionVersion())
    }

    @Test
    fun anInstallFromBeforeTheIntroductionCountsAsHavingSeenIt() = runTest {
        db.settingQueries.putSetting(key = CatalogueImporter.VERSION_KEY, value = "3")
        assertEquals(1, guidance.introductionVersion())
    }
}
