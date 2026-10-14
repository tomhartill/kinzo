package com.tomhartill.kinzo.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Everything is stored in metric; these units only affect what the user sees and types. */
enum class WeightUnit(val label: String) {
    Kilograms("kg"),
    Pounds("lb"),
}

object Weight {
    const val KG_PER_LB = 0.45359237

    fun toKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.Kilograms -> value
        WeightUnit.Pounds -> value * KG_PER_LB
    }

    fun fromKg(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.Kilograms -> kg
        WeightUnit.Pounds -> kg / KG_PER_LB
    }

    /**
     * Formats a stored kg value for display: at most two decimals, trailing zeros dropped,
     * so 82.5 kg shows as "82.5" and 100 kg as "100".
     */
    fun format(kg: Double, unit: WeightUnit): String {
        val value = fromKg(kg, unit)
        val hundredths = (value * 100).roundToLong()
        val whole = abs(hundredths) / 100
        val fraction = abs(hundredths) % 100
        val sign = if (hundredths < 0) "-" else ""
        return when {
            fraction == 0L -> "$sign$whole"
            fraction % 10 == 0L -> "$sign$whole.${fraction / 10}"
            else -> "$sign$whole.${fraction.toString().padStart(2, '0')}"
        }
    }
}
