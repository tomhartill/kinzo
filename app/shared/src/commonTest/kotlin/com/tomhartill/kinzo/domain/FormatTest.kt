package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
    @Test
    fun decimalRoundsAndDropsTrailingZeros() {
        assertEquals("5", Format.decimal(5.0, 2))
        assertEquals("5.25", Format.decimal(5.25, 2))
        assertEquals("5.3", Format.decimal(5.299, 2))
        assertEquals("0.4", Format.decimal(0.4, 2))
        assertEquals("-1.5", Format.decimal(-1.5, 2))
    }

    @Test
    fun thousandsGroupsDigits() {
        assertEquals("0", Format.thousands(0))
        assertEquals("999", Format.thousands(999))
        assertEquals("12,450", Format.thousands(12450))
        assertEquals("1,234,567", Format.thousands(1234567))
    }

    @Test
    fun durationReadsLikeAClock() {
        assertEquals("0:00", Format.duration(0))
        assertEquals("0:45", Format.duration(45))
        assertEquals("1:30", Format.duration(90))
        assertEquals("1:02:03", Format.duration(3723))
        assertEquals("12:34", Format.elapsed(754_999))
    }

    @Test
    fun setsReadNaturally() {
        val kg = WeightUnit.Kilograms
        assertEquals("60 kg × 8", Format.set(MeasurementType.WeightReps, SetValues(reps = 8, weightKg = 60.0), kg))
        assertEquals("60 × 8", Format.set(MeasurementType.WeightReps, SetValues(reps = 8, weightKg = 60.0), kg, compact = true))
        assertEquals("1 rep", Format.set(MeasurementType.Reps, SetValues(reps = 1), kg))
        assertEquals("12 reps", Format.set(MeasurementType.Reps, SetValues(reps = 12), kg))
        assertEquals("1:30", Format.set(MeasurementType.Duration, SetValues(durationS = 90), kg))
        assertEquals("5 km · 25:00", Format.set(MeasurementType.DistanceDuration, SetValues(durationS = 1500, distanceM = 5000.0), kg))
        assertEquals("–", Format.set(MeasurementType.Duration, SetValues(), kg))
    }

    @Test
    fun workoutLengths() {
        assertEquals("1 min", Format.workoutLength(20_000))
        assertEquals("48 min", Format.workoutLength(48 * 60_000L + 30_000))
        assertEquals("1 h 5 min", Format.workoutLength(65 * 60_000L))
    }

    @Test
    fun volumeUsesTheChosenUnit() {
        assertEquals("1,000 kg", Format.volume(1000.0, WeightUnit.Kilograms))
        assertEquals("2,205 lb", Format.volume(1000.0, WeightUnit.Pounds))
    }
}

class ParseTest {
    @Test
    fun decimalAcceptsPointOrComma() {
        assertEquals(62.5, Parse.decimal("62.5"))
        assertEquals(62.5, Parse.decimal(" 62,5 "))
        assertEquals(100.0, Parse.decimal("100"))
        assertEquals(0.5, Parse.decimal(".5"))
        assertEquals(7.0, Parse.decimal("7."))
    }

    @Test
    fun decimalRejectsJunk() {
        assertNull(Parse.decimal(""))
        assertNull(Parse.decimal("."))
        assertNull(Parse.decimal("-5"))
        assertNull(Parse.decimal("1e3"))
        assertNull(Parse.decimal("12.3456"))
        assertNull(Parse.decimal("123456"))
        assertNull(Parse.decimal("abc"))
    }

    @Test
    fun wholeNumbers() {
        assertEquals(8, Parse.wholeNumber("8"))
        assertEquals(0, Parse.wholeNumber("0"))
        assertNull(Parse.wholeNumber("8.5"))
        assertNull(Parse.wholeNumber(""))
        assertNull(Parse.wholeNumber("123456"))
    }

    @Test
    fun durations() {
        assertEquals(90, Parse.duration("90"))
        assertEquals(90, Parse.duration("1:30"))
        assertEquals(3723, Parse.duration("1:02:03"))
        assertNull(Parse.duration("1:75"))
        assertNull(Parse.duration(""))
        assertNull(Parse.duration("1::30"))
        assertNull(Parse.duration("1:2:3:4"))
    }
}
