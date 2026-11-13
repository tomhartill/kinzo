package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.Ids
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Sixteen weeks of made-up training for the demo profile: push, pull and legs three times a
 * week, a treadmill run most Saturdays, steady progress with a deload week, a squat plateau,
 * the odd missed session and the odd bad day. Enough for every kind of progress chart and
 * trend line to show something real. The same seed gives the same history every time; only
 * the dates move, so the last workout is always a couple of days ago.
 */
internal object DemoHistory {
    const val WEEKS = 16
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val DELOAD_WEEK = 7

    // Exercise IDs from the bundled catalogue (files/catalogue.json).
    private const val BENCH = "1a543f99-227d-5eb1-9178-a1d92b0b9cbe"
    private const val MILITARY_PRESS = "8d6f0d52-9c86-53ea-8d94-8ab4170469c6"
    private const val INCLINE_DB_PRESS = "322589b3-7600-5943-a9f9-4f0ca42490fc"
    private const val TRICEPS_PUSHDOWN = "e55ddbfb-db84-53a2-9eab-095837d8e7e3"
    private const val LATERAL_RAISE = "c1a1eb62-ef3f-56e4-b955-a32ae33feb44"
    private const val DEADLIFT = "b5495d15-e711-54d8-b655-57944190cb2a"
    private const val BARBELL_ROW = "f18a46bd-3302-5576-9cc7-8fe6af2e464b"
    private const val LAT_PULLDOWN = "fd038f9c-d204-52f6-90c0-3c43fc69771a"
    private const val PULLUPS = "d6900fc8-ad39-5744-b92b-bd00e605f25a"
    private const val DB_CURL = "9de6b0bb-d9df-5e44-9971-786df62b38d1"
    private const val SQUAT = "9309862a-4fd6-5c67-8ce6-95c316cbe597"
    private const val ROMANIAN_DEADLIFT = "7058a3e8-80c9-52f0-8c32-e0ad1e333ede"
    private const val LEG_PRESS = "bc0cabd2-8159-5555-a325-41996368b83a"
    private const val LEG_CURL = "0ad813bb-78d7-529a-a9c9-2d901d479ed8"
    private const val PLANK = "8b8311c6-a164-5ffc-88cd-7bb7db1110b9"
    private const val TREADMILL_RUN = "2a77f4e9-c491-56aa-b660-ae0d44391eb3"

    /** How one exercise is trained across the block. */
    private sealed interface Plan {
        val exerciseId: String

        /** Weight and reps, adding [perWeek] kilograms a week in [step] jumps. */
        data class Lift(
            override val exerciseId: String,
            val startKg: Double,
            val perWeek: Double,
            val sets: Int,
            val reps: Int,
            val step: Double = 1.25,
            val warmups: Boolean = false,
            // Weeks from which the weight stops going up and reps start to slip.
            val stallFromWeek: Int? = null,
        ) : Plan

        /** Bodyweight reps, slowly climbing. */
        data class Reps(override val exerciseId: String, val sets: Int, val startReps: Int, val endReps: Int) : Plan

        /** A timed hold, a few seconds longer each week. */
        data class Hold(override val exerciseId: String, val sets: Int, val startS: Int, val perWeekS: Int) : Plan

        /** A run of about [distanceM], a little quicker each week. */
        data class Run(override val exerciseId: String, val distanceM: Double, val startPaceSPerKm: Int, val endPaceSPerKm: Int) : Plan
    }

    private data class Session(val split: String, val dayOfWeek: Int, val hourUtc: Int, val plans: List<Plan>)

    private val sessions = listOf(
        Session(
            "push", 0, 7,
            listOf(
                Plan.Lift(BENCH, startKg = 60.0, perWeek = 1.25, sets = 3, reps = 5, warmups = true),
                Plan.Lift(MILITARY_PRESS, startKg = 35.0, perWeek = 0.625, sets = 3, reps = 6),
                Plan.Lift(INCLINE_DB_PRESS, startKg = 20.0, perWeek = 0.5, sets = 3, reps = 10, step = 2.0),
                Plan.Lift(TRICEPS_PUSHDOWN, startKg = 25.0, perWeek = 0.625, sets = 3, reps = 12, step = 2.5),
                Plan.Lift(LATERAL_RAISE, startKg = 8.0, perWeek = 0.25, sets = 3, reps = 15, step = 1.0),
            ),
        ),
        Session(
            "pull", 2, 18,
            listOf(
                Plan.Lift(DEADLIFT, startKg = 100.0, perWeek = 2.5, sets = 1, reps = 5, step = 2.5, warmups = true),
                Plan.Lift(BARBELL_ROW, startKg = 50.0, perWeek = 1.25, sets = 3, reps = 8),
                Plan.Reps(PULLUPS, sets = 3, startReps = 5, endReps = 10),
                Plan.Lift(LAT_PULLDOWN, startKg = 45.0, perWeek = 1.0, sets = 3, reps = 10, step = 2.5),
                Plan.Lift(DB_CURL, startKg = 10.0, perWeek = 0.25, sets = 3, reps = 10, step = 1.0),
            ),
        ),
        Session(
            "legs", 4, 7,
            listOf(
                Plan.Lift(SQUAT, startKg = 80.0, perWeek = 2.5, sets = 3, reps = 5, step = 2.5, warmups = true, stallFromWeek = 11),
                Plan.Lift(ROMANIAN_DEADLIFT, startKg = 70.0, perWeek = 1.25, sets = 3, reps = 8),
                Plan.Lift(LEG_PRESS, startKg = 140.0, perWeek = 5.0, sets = 3, reps = 10, step = 5.0),
                Plan.Lift(LEG_CURL, startKg = 30.0, perWeek = 0.625, sets = 3, reps = 12, step = 2.5),
                Plan.Hold(PLANK, sets = 3, startS = 40, perWeekS = 5),
            ),
        ),
        Session(
            "run", 5, 9,
            listOf(Plan.Run(TREADMILL_RUN, distanceM = 5000.0, startPaceSPerKm = 360, endPaceSPerKm = 315)),
        ),
    )

    /** Writes the history into [db], ending just before [nowMillis]. */
    fun seed(db: KinzoDatabase, nowMillis: Long) {
        val random = Random(20261026)
        // Back to the Monday at midnight UTC (1 January 1970 was a Thursday), then the block's
        // first Monday, so the last week ends before today.
        val days = nowMillis / DAY_MS
        val thisMonday = (days - (days + 3) % 7) * DAY_MS
        val firstMonday = thisMonday - WEEKS * 7 * DAY_MS
        db.transaction {
            for (week in 0 until WEEKS) {
                for (session in sessions) {
                    // About one session in twelve is missed, and the odd run.
                    val skipChance = if (session.split == "run") 0.25 else 0.08
                    if (random.nextDouble() < skipChance) continue
                    val startedAt = firstMonday + (week * 7 + session.dayOfWeek) * DAY_MS +
                        session.hourUtc * 3_600_000L + random.nextLong(0, 40) * 60_000
                    if (startedAt > nowMillis - DAY_MS) continue
                    // One day in fifteen everything feels heavy.
                    val badDay = random.nextDouble() < 1.0 / 15
                    writeSession(db, random, session, week, startedAt, badDay)
                }
            }
        }
    }

    private fun writeSession(db: KinzoDatabase, random: Random, session: Session, week: Int, startedAt: Long, badDay: Boolean) {
        val queries = db.workoutQueries
        val workoutId = Ids.newId()
        queries.insertWorkout(id = workoutId, name = workoutName(session, startedAt), routineId = null, startedAt = startedAt)
        var clock = startedAt + 4 * 60_000
        session.plans.forEachIndexed { position, plan ->
            val workoutExerciseId = Ids.newId()
            val restS = if (plan is Plan.Lift && plan.reps <= 6) 180L else 90L
            queries.insertWorkoutExercise(
                id = workoutExerciseId,
                workoutId = workoutId,
                exerciseId = plan.exerciseId,
                position = position.toLong(),
                restS = if (plan is Plan.Run) 0L else restS,
                now = startedAt,
            )
            val sets = setsFor(plan, week, random, badDay)
            sets.forEachIndexed { index, set ->
                clock += set.durationS?.let { it * 1000L } ?: 45_000L
                val setId = Ids.newId()
                queries.insertSet(
                    id = setId,
                    workoutExerciseId = workoutExerciseId,
                    position = index.toLong(),
                    reps = set.reps?.toLong(),
                    weightKg = set.weightKg,
                    durationS = set.durationS?.toLong(),
                    distanceM = set.distanceM,
                    isWarmup = if (set.warmup) 1L else 0L,
                    now = startedAt,
                )
                queries.completeSet(
                    reps = set.reps?.toLong(),
                    weightKg = set.weightKg,
                    durationS = set.durationS?.toLong(),
                    distanceM = set.distanceM,
                    now = clock,
                    id = setId,
                )
                clock += if (set.warmup) 60_000L else restS * 1000
            }
            clock += 60_000
        }
        queries.finishWorkout(endedAt = clock, id = workoutId)
    }

    private data class DemoSet(
        val reps: Int? = null,
        val weightKg: Double? = null,
        val durationS: Int? = null,
        val distanceM: Double? = null,
        val warmup: Boolean = false,
    )

    private fun setsFor(plan: Plan, week: Int, random: Random, badDay: Boolean): List<DemoSet> {
        val deload = week == DELOAD_WEEK
        return when (plan) {
            is Plan.Lift -> {
                val stalled = plan.stallFromWeek?.let { week >= it } == true
                val weeksOfProgress = if (stalled) plan.stallFromWeek!! else week
                var kg = plan.startKg + plan.perWeek * weeksOfProgress
                if (deload) kg *= 0.85
                if (badDay) kg *= 0.95
                val working = roundTo(kg, plan.step)
                val warmups = if (plan.warmups && !deload) {
                    listOf(
                        DemoSet(reps = 10, weightKg = max(20.0, roundTo(working * 0.4, plan.step)), warmup = true),
                        DemoSet(reps = 5, weightKg = roundTo(working * 0.6, plan.step), warmup = true),
                        DemoSet(reps = 3, weightKg = roundTo(working * 0.8, plan.step), warmup = true),
                    )
                } else {
                    emptyList()
                }
                val setCount = if (deload) max(1, plan.sets - 1) else plan.sets
                warmups + (0 until setCount).map { index ->
                    val last = index == setCount - 1
                    // On a plateau the last set falls short; otherwise it now and then does too.
                    val missed = when {
                        stalled && last -> random.nextInt(1, 3)
                        last && random.nextDouble() < 0.15 -> 1
                        else -> 0
                    }
                    DemoSet(reps = plan.reps - missed, weightKg = working)
                }
            }
            is Plan.Reps -> {
                val progress = week.toDouble() / (WEEKS - 1)
                val top = (plan.startReps + (plan.endReps - plan.startReps) * progress).roundToInt()
                (0 until plan.sets).map { index -> DemoSet(reps = max(1, top - index - if (badDay) 1 else 0)) }
            }
            is Plan.Hold -> {
                val seconds = plan.startS + plan.perWeekS * week - if (badDay) 10 else 0
                (0 until plan.sets).map { index -> DemoSet(durationS = seconds - index * 5) }
            }
            is Plan.Run -> {
                val progress = week.toDouble() / (WEEKS - 1)
                val pace = plan.startPaceSPerKm + (plan.endPaceSPerKm - plan.startPaceSPerKm) * progress +
                    random.nextInt(-8, 9) + if (badDay) 20 else 0
                // Mostly 5 km, sometimes a longer one.
                val distance = if (random.nextDouble() < 0.25) plan.distanceM + 2000 else plan.distanceM
                listOf(DemoSet(durationS = (pace * distance / 1000).roundToInt(), distanceM = distance))
            }
        }
    }

    private fun workoutName(session: Session, startedAt: Long): String {
        val hour = (startedAt % DAY_MS) / 3_600_000
        val time = when {
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            else -> "Evening"
        }
        return "$time ${session.split}"
    }

    private fun roundTo(kg: Double, step: Double): Double = (kg / step).roundToLong() * step
}
