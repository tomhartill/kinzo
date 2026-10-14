package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class WeightTest {
    @Test
    fun kilogramsPassThroughUnchanged() {
        assertEquals(82.5, Weight.toKg(82.5, WeightUnit.Kilograms))
        assertEquals(82.5, Weight.fromKg(82.5, WeightUnit.Kilograms))
    }

    @Test
    fun poundsRoundTripThroughKilograms() {
        val kg = Weight.toKg(225.0, WeightUnit.Pounds)
        assertEquals(102.058, kg, absoluteTolerance = 0.001)
        assertEquals(225.0, Weight.fromKg(kg, WeightUnit.Pounds), absoluteTolerance = 1e-9)
    }

    @Test
    fun formatDropsTrailingZeros() {
        assertEquals("100", Weight.format(100.0, WeightUnit.Kilograms))
        assertEquals("82.5", Weight.format(82.5, WeightUnit.Kilograms))
        assertEquals("1.25", Weight.format(1.25, WeightUnit.Kilograms))
        assertEquals("0.05", Weight.format(0.05, WeightUnit.Kilograms))
    }

    @Test
    fun formatShowsPoundsForStoredKilograms() {
        assertEquals("225", Weight.format(Weight.toKg(225.0, WeightUnit.Pounds), WeightUnit.Pounds))
    }
}
