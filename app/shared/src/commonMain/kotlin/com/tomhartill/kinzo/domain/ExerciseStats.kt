package com.tomhartill.kinzo.domain

object ExerciseStats {
    /**
     * The best working set: heaviest weight (then most reps) for weighted exercises, most
     * reps, longest hold, or furthest distance. Warm-ups don't count. Proper personal
     * records, such as estimated one-rep max, arrive with the progression screens.
     */
    fun bestSet(type: MeasurementType, sets: List<PreviousSet>): SetValues? {
        val working = sets.filterNot { it.isWarmup }.map { it.values }
        return when (type) {
            MeasurementType.WeightReps -> working
                .filter { it.weightKg != null && it.reps != null }
                .maxWithOrNull(compareBy<SetValues>({ it.weightKg }, { it.reps }))
            MeasurementType.Reps -> working.filter { it.reps != null }.maxByOrNull { it.reps!! }
            MeasurementType.Duration -> working.filter { it.durationS != null }.maxByOrNull { it.durationS!! }
            MeasurementType.DistanceDuration -> working
                .filter { it.distanceM != null || it.durationS != null }
                .maxWithOrNull(compareBy<SetValues>({ it.distanceM ?: 0.0 }, { it.durationS ?: 0 }))
        }
    }
}
