package com.tomhartill.kinzo.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign

/** Changing a set's weight by a percentage, and describing the change from one set to the next. */
object WeightChange {
    /** How far the add set slider goes either way, in percent. */
    const val MAX_PERCENT = 50.0

    /** The middle of the slider moves slower than the ends, so small changes are easy to hit. */
    private const val CURVE = 1.6

    /** Near the middle of the slider counts as the same weight, so it's easy to land on. */
    private const val DEAD_ZONE = 0.04f

    /** The smallest change people make, in the unit they see: 1.25 kg or 2.5 lb. */
    private fun step(unit: WeightUnit): Double = when (unit) {
        WeightUnit.Kilograms -> 1.25
        WeightUnit.Pounds -> 2.5
    }

    /**
     * [baseKg] changed by [percent] and rounded to the nearest step in [unit], so the result is
     * a weight that can be loaded. No change keeps [baseKg] exactly, even when it isn't on a step.
     */
    fun adjust(baseKg: Double, percent: Double, unit: WeightUnit): Double {
        if (percent == 0.0) return baseKg
        val step = step(unit)
        val shown = Weight.fromKg(baseKg, unit) * (1 + percent / 100)
        val rounded = (round(shown / step) * step).coerceAtLeast(0.0)
        return SetRules.roundKg(Weight.toKg(rounded, unit))
    }

    /**
     * The change from [fromKg] to [toKg] as "−15%" or "+5%", or null when there's no change.
     * Changes under 1% keep a decimal, so a small step doesn't read as "0%".
     */
    fun label(fromKg: Double, toKg: Double): String? {
        if (fromKg <= 0.0) return null
        val percent = (toKg - fromKg) / fromKg * 100
        if (abs(percent) < 0.05) return null
        val whole = round(percent)
        val digits = if (whole == 0.0) Format.decimal(abs(percent), maxDecimals = 1) else abs(whole).toLong().toString()
        return (if (percent < 0) "−" else "+") + digits + "%"
    }

    /** A place on the slider, from −1 at the left edge through 0 in the middle to 1 at the right, as a percentage. */
    fun sliderPercent(position: Float): Double {
        val clamped = position.coerceIn(-1f, 1f)
        if (abs(clamped) < DEAD_ZONE) return 0.0
        return clamped.sign * abs(clamped).toDouble().pow(CURVE) * MAX_PERCENT
    }

    /** Where [percent] sits on the slider: the inverse of [sliderPercent], for drawing the knob. */
    fun sliderPosition(percent: Double): Float {
        val fraction = (abs(percent) / MAX_PERCENT).coerceAtMost(1.0)
        return (percent.sign * fraction.pow(1 / CURVE)).toFloat()
    }
}
