package com.tomhartill.kinzo.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Calendar helpers. Times are epoch milliseconds; labels use the phone's time zone. */
object Dates {
    fun localDateTime(epochMillis: Long, zone: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)

    /** "Morning workout" and so on, from the local hour the workout started. */
    fun defaultWorkoutName(epochMillis: Long, zone: TimeZone): String = "${partOfDay(epochMillis, zone)} workout"

    /** "Morning", "Afternoon", "Evening" or "Night", from the local hour. */
    fun partOfDay(epochMillis: Long, zone: TimeZone): String =
        when (localDateTime(epochMillis, zone).hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..21 -> "Evening"
            else -> "Night"
        }

    /** Midnight at the start of the Monday of the week containing [epochMillis]. */
    fun startOfWeek(epochMillis: Long, zone: TimeZone): Long {
        val date = localDateTime(epochMillis, zone).date
        val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        return monday.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** "Today", "Yesterday", "Tue 14 Oct", or "Tue 14 Oct 2025" outside the current year. */
    fun dayLabel(epochMillis: Long, nowMillis: Long, zone: TimeZone): String {
        val date = localDateTime(epochMillis, zone).date
        val today = localDateTime(nowMillis, zone).date
        return when (date) {
            today -> "Today"
            today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
            else -> {
                val label = "${weekday(date)} ${date.day} ${monthShort(date)}"
                if (date.year == today.year) label else "$label ${date.year}"
            }
        }
    }

    /** "Wednesday 14 October". */
    fun longDayLabel(epochMillis: Long, zone: TimeZone): String {
        val date = localDateTime(epochMillis, zone).date
        val weekday = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        return "$weekday ${date.day} ${monthLong(date)}"
    }

    /** "October 2026". */
    fun monthLabel(epochMillis: Long, zone: TimeZone): String {
        val date = localDateTime(epochMillis, zone).date
        return "${monthLong(date)} ${date.year}"
    }

    /** "14 Oct", for tight spaces such as chart axes. */
    fun shortDayLabel(epochMillis: Long, zone: TimeZone): String {
        val date = localDateTime(epochMillis, zone).date
        return "${date.day} ${monthShort(date)}"
    }

    /** "18:05". */
    fun timeLabel(epochMillis: Long, zone: TimeZone): String {
        val time = localDateTime(epochMillis, zone)
        return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }

    /** "14 Oct", or "14 Oct 2027" outside the year of [today]. */
    fun dateLabel(date: LocalDate, today: LocalDate): String {
        val label = "${date.day} ${monthShort(date)}"
        return if (date.year == today.year) label else "$label ${date.year}"
    }

    /** "14 Oct – 5 Jan 2027": both ends, each with its year when that isn't this year. */
    fun dateRangeLabel(start: LocalDate, end: LocalDate, today: LocalDate): String =
        "${dateLabel(start, today)} – ${dateLabel(end, today)}"

    private fun weekday(date: LocalDate): String = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

    private fun monthLong(date: LocalDate): String = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun monthShort(date: LocalDate): String = monthLong(date).take(3)
}
