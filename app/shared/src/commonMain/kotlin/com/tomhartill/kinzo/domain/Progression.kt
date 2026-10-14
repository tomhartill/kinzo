package com.tomhartill.kinzo.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** One value on a progression chart: what a session scored, and when. */
data class ProgressPoint(val atMillis: Long, val value: Double)

/** What a progression chart plots for each session. Weights are kilograms, times seconds. */
enum class ProgressMetric(val label: String) {
    EstimatedOneRepMax("Est. 1RM"),
    Heaviest("Heaviest"),
    Volume("Volume"),
    Reps("Reps"),
    Duration("Time"),
    Distance("Distance");

    companion object {
        /** The metrics worth charting for an exercise, most useful first. */
        fun forType(type: MeasurementType): List<ProgressMetric> = when (type) {
            MeasurementType.WeightReps -> listOf(EstimatedOneRepMax, Heaviest, Volume)
            MeasurementType.Reps -> listOf(Reps)
            MeasurementType.Duration -> listOf(Duration)
            MeasurementType.DistanceDuration -> listOf(Distance, Duration)
        }
    }
}

object Progression {
    /** Epley's estimate of the most you could lift once, from a set of [reps] at [weightKg]. */
    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

    /**
     * One point per session, oldest first. Warm-ups don't count, and sessions with nothing to
     * measure for [metric] are left out rather than plotted as zero.
     */
    fun series(metric: ProgressMetric, sessions: List<ExerciseSession>): List<ProgressPoint> =
        sessions
            .mapNotNull { session ->
                val working = session.sets.filterNot { it.isWarmup }.map { it.values }
                sessionValue(metric, working)?.let { ProgressPoint(session.startedAt, it) }
            }
            .sortedBy { it.atMillis }

    private fun sessionValue(metric: ProgressMetric, sets: List<SetValues>): Double? = when (metric) {
        ProgressMetric.EstimatedOneRepMax -> sets
            .mapNotNull { set -> set.weightKg?.let { w -> set.reps?.takeIf { it > 0 }?.let { estimatedOneRepMax(w, it) } } }
            .maxOrNull()
        ProgressMetric.Heaviest -> sets.mapNotNull { it.weightKg }.maxOrNull()
        ProgressMetric.Volume -> sets.sumOf { it.volumeKg }.takeIf { it > 0 }
        ProgressMetric.Reps -> sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
        ProgressMetric.Duration -> sets.mapNotNull { it.durationS }.maxOrNull()?.toDouble()
        ProgressMetric.Distance -> sets.mapNotNull { it.distanceM }.maxOrNull()
    }
}

/** A chart's value axis: round bounds and the gap between gridlines. */
data class ChartScale(val min: Double, val max: Double, val step: Double) {
    companion object {
        /**
         * Round bounds around [low]..[high] with about [ticks] gridlines on 1, 2, 2.5 or 5 steps,
         * so labels read 80, 85, 90 rather than 81.3, 86.7. Never goes below zero.
         */
        fun around(low: Double, high: Double, ticks: Int = 4): ChartScale {
            // A flat line still needs some room above and below it.
            val span = if (high > low) high - low else max(high * 0.1, 1.0)
            val rough = span / ticks
            val magnitude = 10.0.pow(floor(log10(rough)))
            val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
            // Keep the line off the top and bottom edges.
            val padding = if (high > low) span / 20 else span / 2
            val min = (floor((low - padding) / step) * step).coerceAtLeast(0.0)
            val max = ceil((high + padding) / step) * step
            return ChartScale(min, if (max > min) max else min + step, step)
        }
    }
}
