package com.tomhartill.kinzo.domain

/** Everything is stored in metric; these units only affect what the user sees and types. */
enum class WeightUnit(val label: String, val key: String) {
    Kilograms("kg", "kg"),
    Pounds("lb", "lb");

    companion object {
        fun fromKey(key: String?): WeightUnit = entries.firstOrNull { it.key == key } ?: Kilograms
    }
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
    fun format(kg: Double, unit: WeightUnit): String = Format.decimal(fromKg(kg, unit), maxDecimals = 2)
}
