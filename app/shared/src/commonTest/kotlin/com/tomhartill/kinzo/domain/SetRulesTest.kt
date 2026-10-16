package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetRulesTest {
    @Test
    fun blankFieldsTakeLastTimesValues() {
        val resolved = SetRules.resolveForCompletion(
            MeasurementType.WeightReps,
            entered = SetValues(weightKg = 62.5),
            previous = SetValues(reps = 8, weightKg = 60.0),
        )
        assertEquals(SetValues(reps = 8, weightKg = 62.5), resolved)
    }

    @Test
    fun weightedSetsNeedRepsAndWeight() {
        assertNull(SetRules.resolveForCompletion(MeasurementType.WeightReps, SetValues(reps = 8), previous = null))
        assertNull(SetRules.resolveForCompletion(MeasurementType.WeightReps, SetValues(weightKg = 60.0), previous = null))
        assertNull(SetRules.resolveForCompletion(MeasurementType.WeightReps, SetValues(reps = 0, weightKg = 60.0), previous = null))
        // An empty bar or an assisted machine at zero is still a real set.
        assertEquals(
            SetValues(reps = 10, weightKg = 0.0),
            SetRules.resolveForCompletion(MeasurementType.WeightReps, SetValues(reps = 10, weightKg = 0.0), previous = null),
        )
    }

    @Test
    fun otherTypesNeedTheirOwnNumbers() {
        assertEquals(SetValues(reps = 12), SetRules.resolveForCompletion(MeasurementType.Reps, SetValues(reps = 12, weightKg = 5.0), null))
        assertNull(SetRules.resolveForCompletion(MeasurementType.Duration, SetValues(reps = 12), null))
        assertEquals(SetValues(durationS = 60), SetRules.resolveForCompletion(MeasurementType.Duration, SetValues(durationS = 60), null))
        assertEquals(
            SetValues(distanceM = 5000.0),
            SetRules.resolveForCompletion(MeasurementType.DistanceDuration, SetValues(distanceM = 5000.0), null),
        )
        assertNull(SetRules.resolveForCompletion(MeasurementType.DistanceDuration, SetValues(), null))
    }

    @Test
    fun kilogramsRoundToThreeDecimals() {
        assertEquals(6.804, SetRules.roundKg(Weight.toKg(15.0, WeightUnit.Pounds)))
        assertEquals("15", Weight.format(SetRules.roundKg(Weight.toKg(15.0, WeightUnit.Pounds)), WeightUnit.Pounds))
    }

    @Test
    fun warmupsAreLabelledAndSkippedInNumbering() {
        assertEquals(listOf("W", "W", "1", "2", "3"), SetRules.labels(listOf(true, true, false, false, false)))
        assertEquals(listOf("1", "W", "2"), SetRules.labels(listOf(false, true, false)))
    }
}
