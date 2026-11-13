package com.tomhartill.kinzo.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class DatesTest {
    private val zone = TimeZone.UTC

    private fun at(text: String): Long = LocalDateTime.parse(text).toInstant(zone).toEpochMilliseconds()

    @Test
    fun workoutNamesFollowTheTimeOfDay() {
        assertEquals("Morning workout", Dates.defaultWorkoutName(at("2026-10-14T07:30"), zone))
        assertEquals("Afternoon workout", Dates.defaultWorkoutName(at("2026-10-14T12:00"), zone))
        assertEquals("Evening workout", Dates.defaultWorkoutName(at("2026-10-14T18:15"), zone))
        assertEquals("Night workout", Dates.defaultWorkoutName(at("2026-10-14T23:10"), zone))
        assertEquals("Night workout", Dates.defaultWorkoutName(at("2026-10-14T02:00"), zone))
        assertEquals("Afternoon", Dates.partOfDay(at("2026-10-14T16:59"), zone))
        assertEquals("Evening", Dates.partOfDay(at("2026-10-14T17:00"), zone))
    }

    @Test
    fun weeksStartOnMonday() {
        // 14 October 2026 is a Wednesday.
        assertEquals(at("2026-10-12T00:00"), Dates.startOfWeek(at("2026-10-14T18:00"), zone))
        assertEquals(at("2026-10-12T00:00"), Dates.startOfWeek(at("2026-10-12T00:00"), zone))
        assertEquals(at("2026-10-12T00:00"), Dates.startOfWeek(at("2026-10-18T23:59"), zone))
    }

    @Test
    fun dayLabelsAreRelativeWhenClose() {
        val now = at("2026-10-14T18:00")
        assertEquals("Today", Dates.dayLabel(at("2026-10-14T07:00"), now, zone))
        assertEquals("Yesterday", Dates.dayLabel(at("2026-10-13T21:00"), now, zone))
        assertEquals("Mon 12 Oct", Dates.dayLabel(at("2026-10-12T09:00"), now, zone))
        assertEquals("Wed 31 Dec 2025", Dates.dayLabel(at("2025-12-31T09:00"), now, zone))
    }

    @Test
    fun monthAndTimeLabels() {
        assertEquals("October 2026", Dates.monthLabel(at("2026-10-14T18:00"), zone))
        assertEquals("Wednesday 14 October", Dates.longDayLabel(at("2026-10-14T18:00"), zone))
        assertEquals("08:05", Dates.timeLabel(at("2026-10-14T08:05"), zone))
    }

    @Test
    fun calendarDatesShowTheYearOnlyWhenItIsNotThisOne() {
        val today = LocalDate(2026, 10, 14)
        assertEquals("14 Oct", Dates.dateLabel(LocalDate(2026, 10, 14), today))
        assertEquals("5 Jan 2027", Dates.dateLabel(LocalDate(2027, 1, 5), today))
        assertEquals("14 Oct – 5 Jan 2027", Dates.dateRangeLabel(LocalDate(2026, 10, 14), LocalDate(2027, 1, 5), today))
    }
}
