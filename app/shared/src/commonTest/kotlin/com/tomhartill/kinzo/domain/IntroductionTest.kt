package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntroductionTest {
    @Test
    fun aNewInstallSeesEveryPage() {
        assertEquals(IntroductionStep.entries.toList(), Introduction.stepsAfter(0))
    }

    @Test
    fun someoneUpToDateSeesNothing() {
        assertTrue(Introduction.stepsAfter(Introduction.VERSION).isEmpty())
    }

    @Test
    fun everyPageBelongsToAShippedVersion() {
        assertTrue(IntroductionStep.entries.all { it.sinceVersion in 1..Introduction.VERSION })
    }
}
