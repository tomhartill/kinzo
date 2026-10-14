package com.tomhartill.kinzo.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Turns stored values into the short strings the UI shows. */
object Format {
    /** Rounds to at most [maxDecimals] places and drops trailing zeros: 82.50 -> "82.5". */
    fun decimal(value: Double, maxDecimals: Int): String {
        var scale = 1L
        repeat(maxDecimals) { scale *= 10 }
        val scaled = (value * scale).roundToLong()
        val sign = if (scaled < 0) "-" else ""
        val whole = abs(scaled) / scale
        val fraction = (abs(scaled) % scale).toString().padStart(maxDecimals, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
    }

    /** "12,450". */
    fun thousands(value: Long): String {
        val digits = abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    /** Seconds as a clock: "0:45", "1:30", "1:02:03". */
    fun duration(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        return if (hours > 0) {
            "$hours:${minutes.pad2()}:${rest.pad2()}"
        } else {
            "$minutes:${rest.pad2()}"
        }
    }

    fun duration(totalSeconds: Int): String = duration(totalSeconds.toLong())

    /** Milliseconds as a running clock, rounded down to the second. */
    fun elapsed(millis: Long): String = duration(millis.coerceAtLeast(0) / 1000)

    /** A workout's length in words: "48 min", "1 h 5 min". Under a minute counts as one. */
    fun workoutLength(millis: Long): String {
        val minutes = (millis / 60_000).coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    /** Metres shown as kilometres, for example 5250.0 -> "5.25". */
    fun kilometres(metres: Double): String = decimal(metres / 1000.0, maxDecimals = 2)

    /** Total volume with its unit, for example "12,450 kg". */
    fun volume(kg: Double, unit: WeightUnit): String =
        "${thousands(Weight.fromKg(kg, unit).roundToLong())} ${unit.label}"

    /**
     * One set in words: "60 kg × 8", "12 reps", "1:30" or "5 km · 25:00". [compact] leaves the
     * weight unit out ("60 × 8") for tight columns where the header already names it.
     */
    fun set(type: MeasurementType, values: SetValues, unit: WeightUnit, compact: Boolean = false): String {
        val blank = "–"
        return when (type) {
            MeasurementType.WeightReps -> {
                val weight = values.weightKg?.let { Weight.format(it, unit) } ?: blank
                val reps = values.reps?.toString() ?: blank
                if (compact) "$weight × $reps" else "$weight ${unit.label} × $reps"
            }
            MeasurementType.Reps -> values.reps?.let { if (it == 1) "1 rep" else "$it reps" } ?: blank
            MeasurementType.Duration -> values.durationS?.let { duration(it) } ?: blank
            MeasurementType.DistanceDuration -> listOfNotNull(
                values.distanceM?.let { "${kilometres(it)} km" },
                values.durationS?.let { duration(it) },
            ).joinToString(" · ").ifEmpty { blank }
        }
    }

    private fun Long.pad2(): String = toString().padStart(2, '0')
}

/** Reads what the user typed. Every function returns null for blank or invalid input. */
object Parse {
    private val decimalPattern = Regex("""^(\d{1,5}(\.\d{0,3})?|\.\d{1,3})$""")

    /** A non-negative number with up to three decimals. Accepts a comma as the decimal mark. */
    fun decimal(text: String): Double? {
        val normalised = text.trim().replace(',', '.')
        if (!decimalPattern.matches(normalised)) return null
        return normalised.toDoubleOrNull()
    }

    /** A whole number of up to five digits, for reps. */
    fun wholeNumber(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 5 || !trimmed.all { it in '0'..'9' }) return null
        return trimmed.toInt()
    }

    /** "90" (seconds), "1:30" or "1:02:03". Minutes and seconds after the first part must be under 60. */
    fun duration(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.size > 3 || parts.any { part -> part.isEmpty() || part.length > 5 || !part.all { it in '0'..'9' } }) {
            return null
        }
        val numbers = parts.map { it.toInt() }
        if (numbers.drop(1).any { it >= 60 }) return null
        return numbers.fold(0) { total, part -> total * 60 + part }
    }
}
