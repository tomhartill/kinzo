package com.tomhartill.kinzo.domain

object SetRules {
    /** A new exercise in a workout starts with as many sets as last time, within these bounds. */
    const val MIN_INITIAL_SETS = 1
    const val MAX_INITIAL_SETS = 10

    /**
     * The values to save when the user ticks a set as done. Blank fields take last time's
     * value, so repeating a session is one tap per set. Returns null when the set is still
     * missing something its type needs, such as reps.
     */
    fun resolveForCompletion(type: MeasurementType, entered: SetValues, previous: SetValues?): SetValues? =
        fillBlanks(type, entered, previous).takeIf { isComplete(type, it) }

    /** [entered] with blanks taken from [previous], keeping only what [type] records. */
    fun fillBlanks(type: MeasurementType, entered: SetValues, previous: SetValues?): SetValues = SetValues(
        reps = entered.reps ?: previous?.reps,
        weightKg = entered.weightKg ?: previous?.weightKg,
        durationS = entered.durationS ?: previous?.durationS,
        distanceM = entered.distanceM ?: previous?.distanceM,
    ).keepOnly(type)

    /** Whether a set with these values can count as done. */
    fun isComplete(type: MeasurementType, values: SetValues): Boolean = when (type) {
        MeasurementType.WeightReps -> (values.reps ?: 0) > 0 && values.weightKg != null
        MeasurementType.Reps -> (values.reps ?: 0) > 0
        MeasurementType.Duration -> (values.durationS ?: 0) > 0
        MeasurementType.DistanceDuration -> (values.distanceM ?: 0.0) > 0.0 || (values.durationS ?: 0) > 0
    }

    /** "W" for warm-ups; working sets are numbered 1, 2, 3 without counting the warm-ups. */
    fun labels(warmups: List<Boolean>): List<String> {
        var working = 0
        return warmups.map { isWarmup -> if (isWarmup) "W" else (++working).toString() }
    }

    /** Kilograms are stored to three decimals, which keeps pound entries exact (see Weight). */
    fun roundKg(kg: Double): Double = kotlin.math.round(kg * 1000.0) / 1000.0
}
