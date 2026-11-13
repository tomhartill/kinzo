package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressionTest {
    private fun set(weight: Double? = null, reps: Int? = null, seconds: Int? = null, warmup: Boolean = false) =
        PreviousSet(SetValues(reps = reps, weightKg = weight, durationS = seconds), isWarmup = warmup)

    private fun session(at: Long, vararg sets: PreviousSet) = ExerciseSession("w$at", "Workout", at, sets.toList())

    @Test
    fun epley() {
        assertEquals(100.0, Progression.estimatedOneRepMax(100.0, 1))
        assertEquals(120.0, Progression.estimatedOneRepMax(100.0, 6))
    }

    @Test
    fun oneWorkingValuePerSessionOldestFirst() {
        // History arrives newest first.
        val sessions = listOf(
            session(2, set(140.0, 1, warmup = true), set(100.0, 6), set(105.0, 3)),
            session(1, set(90.0, 6), set(90.0, 5)),
        )
        assertEquals(
            listOf(ProgressPoint(1, 108.0), ProgressPoint(2, 120.0)),
            Progression.series(ProgressMetric.EstimatedOneRepMax, sessions),
        )
        assertEquals(listOf(90.0, 105.0), Progression.series(ProgressMetric.Heaviest, sessions).map { it.value })
        assertEquals(listOf(990.0, 915.0), Progression.series(ProgressMetric.Volume, sessions).map { it.value })
    }

    @Test
    fun sessionsWithNothingToMeasureAreSkipped() {
        val sessions = listOf(session(1, set(60.0, 10, warmup = true)), session(2, set(seconds = 60)))
        assertEquals(emptyList(), Progression.series(ProgressMetric.Heaviest, sessions))
        assertEquals(listOf(ProgressPoint(2, 60.0)), Progression.series(ProgressMetric.Duration, sessions))
    }

    @Test
    fun scaleRoundsToFriendlySteps() {
        assertEquals(ChartScale(75.0, 105.0, 5.0), ChartScale.around(80.0, 100.0))
        assertEquals(ChartScale(1000.0, 5000.0, 1000.0), ChartScale.around(1200.0, 4800.0))
        assertEquals(ChartScale(95.0, 105.0, 2.5), ChartScale.around(100.0, 100.0))
        assertEquals(0.0, ChartScale.around(0.0, 3.0).min)
    }
}
