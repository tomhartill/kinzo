package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseStatsTest {
    private fun set(weight: Double? = null, reps: Int? = null, seconds: Int? = null, metres: Double? = null, warmup: Boolean = false) =
        PreviousSet(SetValues(reps = reps, weightKg = weight, durationS = seconds, distanceM = metres), isWarmup = warmup)

    @Test
    fun heaviestThenMostReps() {
        val sets = listOf(set(100.0, 3, warmup = true), set(80.0, 8), set(85.0, 5), set(85.0, 6))
        assertEquals(SetValues(reps = 6, weightKg = 85.0), ExerciseStats.bestSet(MeasurementType.WeightReps, sets))
    }

    @Test
    fun otherTypes() {
        assertEquals(SetValues(reps = 15), ExerciseStats.bestSet(MeasurementType.Reps, listOf(set(reps = 12), set(reps = 15))))
        assertEquals(SetValues(durationS = 90), ExerciseStats.bestSet(MeasurementType.Duration, listOf(set(seconds = 60), set(seconds = 90))))
        assertEquals(
            SetValues(durationS = 1500, distanceM = 5000.0),
            ExerciseStats.bestSet(MeasurementType.DistanceDuration, listOf(set(seconds = 1200, metres = 4000.0), set(seconds = 1500, metres = 5000.0))),
        )
    }

    @Test
    fun nothingWhenOnlyWarmups() {
        assertNull(ExerciseStats.bestSet(MeasurementType.WeightReps, listOf(set(60.0, 10, warmup = true))))
    }
}
