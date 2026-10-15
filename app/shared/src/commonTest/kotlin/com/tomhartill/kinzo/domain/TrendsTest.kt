package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendsTest {
    @Test
    fun aRisingLineTrendsUpAndAFallingOneDown() {
        assertEquals(TrendDirection.Up, Trends.direction(listOf(100.0, 102.5, 105.0, 107.5)))
        assertEquals(TrendDirection.Down, Trends.direction(listOf(107.5, 105.0, 102.5, 100.0)))
    }

    @Test
    fun smallWobblesReadAsFlat() {
        assertEquals(TrendDirection.Flat, Trends.direction(listOf(100.0, 100.5, 99.5, 100.0)))
        assertEquals(TrendDirection.Flat, Trends.direction(listOf(60.0, 60.0)))
    }

    @Test
    fun oneBadLastSessionDoesNotFlipASteadyClimb() {
        assertEquals(TrendDirection.Up, Trends.direction(listOf(80.0, 85.0, 90.0, 95.0, 100.0, 88.0)))
    }

    @Test
    fun aTrendNeedsTwoPoints() {
        assertNull(Trends.of(emptyList()))
        assertNull(Trends.of(listOf(100.0)))
        assertEquals(Trend(listOf(100.0, 110.0), TrendDirection.Up), Trends.of(listOf(100.0, 110.0)))
    }

    @Test
    fun exercisesKeepOnlyTheirLatestSessionsOfTheirMainMetric() {
        val sessions = (1..15).map { day ->
            ExerciseSession(
                workoutId = "w$day",
                workoutName = "Push",
                startedAt = day * 1_000L,
                sets = listOf(
                    PreviousSet(SetValues(weightKg = 20.0, reps = 10), isWarmup = true),
                    PreviousSet(SetValues(weightKg = 60.0 + day, reps = 1), isWarmup = false),
                ),
            )
        }.reversed()
        val values = Trends.exerciseValues(MeasurementType.WeightReps, sessions)
        assertEquals(Trends.SESSIONS, values.size)
        assertEquals(64.0, values.first())
        assertEquals(75.0, values.last())
    }

    @Test
    fun valuesSurviveTheCache() {
        val values = listOf(100.0, 102.5, 97.333)
        assertEquals(values, Trends.decode(Trends.encode(values)))
        assertEquals(emptyList<Double>(), Trends.decode(""))
    }
}
