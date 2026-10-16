package com.tomhartill.kinzo.domain

import kotlin.math.abs

enum class TrendDirection { Up, Down, Flat }

/**
 * A short run of recent values for a sparkline, oldest first, and which way they're heading.
 * Values are in the metric's base unit; a sparkline only cares about their shape.
 */
data class Trend(val values: List<Double>, val direction: TrendDirection)

/**
 * Sparkline trends. The maths is shared by every kind of subject: an exercise today, and
 * later a muscle, a muscle group or upper and lower body, which only differ in where their
 * per-session values come from.
 */
object Trends {
    /** How many recent sessions a sparkline shows. */
    const val SESSIONS = 12

    /**
     * Bumped whenever the values or the direction rule change, so the cached trends are
     * rebuilt from history on the next launch.
     */
    const val VERSION = 1

    /**
     * A change smaller than this share of the average reads as flat, so a 0.5 kg wobble on a
     * 100 kg lift doesn't show an arrow.
     */
    private const val FLAT_SHARE = 0.02

    /** The one metric an exercise's sparkline follows: the first one its progress chart offers. */
    fun metricFor(type: MeasurementType): ProgressMetric = ProgressMetric.forType(type).first()

    /** The values to cache for an exercise: one per session, oldest first, the last [SESSIONS]. */
    fun exerciseValues(type: MeasurementType, sessions: List<ExerciseSession>): List<Double> =
        Progression.series(metricFor(type), sessions).takeLast(SESSIONS).map { it.value }

    /** A trend from cached values, or null with fewer than two, since one point isn't a line. */
    fun of(values: List<Double>): Trend? =
        if (values.size < 2) null else Trend(values, direction(values))

    /**
     * Up or down by the slope of a straight line fitted through the values, rather than the
     * first against the last, so one off day at either end doesn't flip the arrow.
     */
    fun direction(values: List<Double>): TrendDirection {
        if (values.size < 2) return TrendDirection.Flat
        val n = values.size
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var covariance = 0.0
        var varianceX = 0.0
        values.forEachIndexed { x, y ->
            covariance += (x - meanX) * (y - meanY)
            varianceX += (x - meanX) * (x - meanX)
        }
        // How far the fitted line climbs from the first session to the last.
        val change = covariance / varianceX * (n - 1)
        return when {
            meanY == 0.0 || abs(change) < abs(meanY) * FLAT_SHARE -> TrendDirection.Flat
            change > 0 -> TrendDirection.Up
            else -> TrendDirection.Down
        }
    }

    /** Values as stored in the trend table: plain decimals, comma-separated. */
    fun encode(values: List<Double>): String = values.joinToString(",")

    fun decode(text: String): List<Double> =
        if (text.isEmpty()) emptyList() else text.split(',').mapNotNull { it.toDoubleOrNull() }
}
