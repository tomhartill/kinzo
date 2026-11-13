package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeightChangeTest {
    @Test
    fun adjustRoundsToTheNearestKilogramStep() {
        assertEquals(85.0, WeightChange.adjust(100.0, -15.0, WeightUnit.Kilograms))
        // 85 kg less 20% is 68 kg; the nearest 1.25 kg step is 67.5 kg.
        assertEquals(67.5, WeightChange.adjust(85.0, -20.0, WeightUnit.Kilograms))
        assertEquals(83.75, WeightChange.adjust(85.0, -1.5, WeightUnit.Kilograms))
    }

    @Test
    fun adjustRoundsToTheNearestPoundStep() {
        val base = Weight.toKg(225.0, WeightUnit.Pounds)
        val result = WeightChange.adjust(base, -10.0, WeightUnit.Pounds)
        // 202.5 lb is already on a 2.5 lb step.
        assertEquals(202.5, Weight.fromKg(result, WeightUnit.Pounds), absoluteTolerance = 0.01)
    }

    @Test
    fun noChangeKeepsAnOffStepWeight() {
        assertEquals(83.0, WeightChange.adjust(83.0, 0.0, WeightUnit.Kilograms))
    }

    @Test
    fun adjustNeverGoesBelowZero() {
        assertEquals(0.0, WeightChange.adjust(1.0, -50.0, WeightUnit.Kilograms))
    }

    @Test
    fun labelShowsWholePercentagesWithASign() {
        assertEquals("−15%", WeightChange.label(100.0, 85.0))
        assertEquals("+5%", WeightChange.label(100.0, 105.0))
        assertEquals("−21%", WeightChange.label(85.0, 67.5))
    }

    @Test
    fun labelKeepsADecimalUnderOnePercent() {
        assertEquals("+0.4%", WeightChange.label(300.0, 301.25))
    }

    @Test
    fun labelIsNullWithoutAChange() {
        assertNull(WeightChange.label(100.0, 100.0))
        assertNull(WeightChange.label(0.0, 20.0))
    }

    @Test
    fun sliderMiddleIsTheSameWeightAndEdgesAreFiftyPercent() {
        assertEquals(0.0, WeightChange.sliderPercent(0.02f))
        assertEquals(-50.0, WeightChange.sliderPercent(-1f))
        assertEquals(50.0, WeightChange.sliderPercent(1.4f))
    }

    @Test
    fun sliderPositionUndoesSliderPercent() {
        val percent = WeightChange.sliderPercent(0.6f)
        assertEquals(0.6f, WeightChange.sliderPosition(percent), absoluteTolerance = 1e-4f)
    }

    @Test
    fun nextSetCopiesTheLastSetAndChangesItsWeight() {
        val exercise = exercise(
            sets = listOf(set(100.0, 5), set(null, null)),
            previous = listOf(PreviousSet(SetValues(reps = 5, weightKg = 100.0), false), PreviousSet(SetValues(reps = 8, weightKg = 90.0), false)),
        )
        // The blank last set counts as last time's 90 x 8.
        assertEquals(SetValues(reps = 8, weightKg = 76.25), exercise.nextSetValues(-15.0, WeightUnit.Kilograms))
        assertEquals(SetValues(reps = 8, weightKg = 90.0), exercise.nextSetValues(0.0, WeightUnit.Kilograms))
    }

    @Test
    fun weightChangeComparesWithTheSetAbove() {
        val exercise = exercise(sets = listOf(set(100.0, 5), set(85.0, 8), set(null, null), set(85.0, 8, warmup = true)), previous = emptyList())
        assertNull(exercise.weightChange(0))
        assertEquals("−15%", exercise.weightChange(1))
        // No weight of its own yet.
        assertNull(exercise.weightChange(2))
        // A warm-up after a working set.
        assertNull(exercise.weightChange(3))
    }

    private fun set(kg: Double?, reps: Int?, warmup: Boolean = false) =
        LoggedSet(id = Ids.newId(), values = SetValues(reps = reps, weightKg = kg), isWarmup = warmup, completedAt = null)

    private fun exercise(sets: List<LoggedSet>, previous: List<PreviousSet>) = WorkoutExercise(
        id = "we",
        exerciseId = "bench",
        name = "Bench press",
        measurementType = MeasurementType.WeightReps,
        sets = sets,
        previous = previous,
    )
}
