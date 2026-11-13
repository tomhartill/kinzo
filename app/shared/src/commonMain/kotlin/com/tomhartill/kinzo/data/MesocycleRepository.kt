package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.Ids
import com.tomhartill.kinzo.domain.Mesocycle
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.currentOn
import com.tomhartill.kinzo.domain.startBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Mesocycles, the blocks of training the user's own workouts belong to. There's always a
 * current one once [ensureCurrent] has run: the latest that has started by today, or the first
 * coming up when none has. Their starts stay in order, each after the one before.
 */
class MesocycleRepository(
    private val db: KinzoDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val queries get() = db.mesocycleQueries

    /** Every mesocycle, the latest start first. [currentOn] picks out the current one. */
    fun mesocycles(): Flow<List<Mesocycle>> =
        queries.mesocycles(::toMesocycle).asFlow().mapToList(io).distinctUntilChanged()

    /** The current mesocycle, or null before [ensureCurrent] has made the first one. */
    fun current(): Flow<Mesocycle?> = mesocycles().map { it.currentOn(today()) }.distinctUntilChanged()

    /** Today in the phone's time zone. */
    fun today(): LocalDate = clock.now().toLocalDateTime(zone()).date

    /** The end date a mesocycle starting on [start] gets unless the user picks another. */
    fun defaultEnd(start: LocalDate): LocalDate =
        start.plus(Mesocycle.DEFAULT_WEEKS * 7 - 1, DateTimeUnit.DAY)

    /**
     * Makes sure there's a current mesocycle, and puts any of the user's workouts that don't
     * belong to one into it. Runs at every launch. When there's none, "Mesocycle 1" starts on
     * the day the user first trained or saved a workout in the app, or today for a new user.
     */
    suspend fun ensureCurrent(): Mesocycle = withContext(io) {
        db.transactionWithResult {
            val today = today()
            val current = queries.currentMesocycle(today.toString(), ::toMesocycle).executeAsOneOrNull()
                ?: firstTrainingDay(today).let { insert(name = nameFor(1), start = it, end = defaultEnd(it)) }
            db.routineQueries.assignMesocycle(mesocycleId = current.id)
            current
        }
    }

    // The earliest day with a logged workout or one of the user's own saved, up to today.
    private fun firstTrainingDay(today: LocalDate): LocalDate {
        val first = listOfNotNull(
            queries.firstWorkoutStartedAt().executeAsOne().first,
            queries.firstOwnRoutineCreatedAt().executeAsOne().first,
        ).minOrNull() ?: return today
        return minOf(Instant.fromEpochMilliseconds(first).toLocalDateTime(zone()).date, today)
    }

    /** "Mesocycle 2" when there's one already, and so on. */
    suspend fun nextName(): String = withContext(io) {
        nameFor(queries.countMesocycles().executeAsOne().toInt() + 1)
    }

    /**
     * Renames a mesocycle or moves its dates. Returns false and changes nothing when [end] is
     * before [start], the mesocycle is gone, or [start] would move it past a neighbour's start
     * (see [startBounds]). A blank name keeps the old one.
     */
    suspend fun update(id: String, name: String, start: LocalDate, end: LocalDate): Boolean = withContext(io) {
        if (end < start) return@withContext false
        db.transactionWithResult {
            val all = queries.mesocycles(::toMesocycle).executeAsList()
            val existing = all.firstOrNull { it.id == id } ?: return@transactionWithResult false
            val (after, before) = all.startBounds(existing)
            if (after != null && start <= after.start) return@transactionWithResult false
            if (before != null && start >= before.start) return@transactionWithResult false
            queries.updateMesocycle(
                name = name.trim().ifEmpty { existing.name },
                startDate = start.toString(),
                endDate = end.toString(),
                now = now(),
                id = id,
            )
            true
        }
    }

    /**
     * Adds the next mesocycle from [start]. It becomes the current one on that day; until then
     * the one before carries on, and it ends the day before if it would otherwise overlap. With
     * [copyWorkouts], the workouts of the one before are copied into the new one, ready to
     * change; without, it starts empty. Returns null and changes nothing when [end] is before
     * [start], or [start] isn't after the latest mesocycle's start.
     */
    suspend fun startNext(name: String, start: LocalDate, end: LocalDate, copyWorkouts: Boolean): Mesocycle? =
        withContext(io) {
            if (end < start) return@withContext null
            db.transactionWithResult {
                val previous = queries.latestMesocycle(::toMesocycle).executeAsOneOrNull()
                if (previous != null && start <= previous.start) return@transactionWithResult null
                if (previous != null && previous.end >= start) {
                    queries.updateMesocycle(
                        name = previous.name,
                        startDate = previous.start.toString(),
                        endDate = start.minus(1, DateTimeUnit.DAY).toString(),
                        now = now(),
                        id = previous.id,
                    )
                }
                val count = queries.countMesocycles().executeAsOne().toInt()
                val next = insert(name = name.trim().ifEmpty { nameFor(count + 1) }, start = start, end = end)
                if (previous != null && copyWorkouts) copyWorkouts(from = previous.id, to = next.id)
                next
            }
        }

    private fun insert(name: String, start: LocalDate, end: LocalDate): Mesocycle {
        val mesocycle = Mesocycle(id = Ids.newId(), name = name, start = start, end = end)
        queries.insertMesocycle(
            id = mesocycle.id,
            name = mesocycle.name,
            startDate = start.toString(),
            endDate = end.toString(),
            now = now(),
        )
        return mesocycle
    }

    /** Copies the user's workouts with new IDs, keeping the order the library shows them in. */
    private fun copyWorkouts(from: String, to: String) {
        val routines = db.routineQueries
        val now = now()
        routines.routinesInMesocycle(from).executeAsList()
            .sortedByDescending { maxOf(it.last_used_at ?: 0, it.updated_at) }
            .forEachIndexed { index, routine ->
                val copyId = Ids.newId()
                // A millisecond apart, newest first, so the copies list in the same order.
                val stamp = now - index
                routines.upsertRoutine(
                    id = copyId,
                    name = routine.name,
                    notes = routine.notes,
                    createdBy = RoutineSource.User.key,
                    workoutType = routine.workout_type,
                    splitId = routine.split_id,
                    mesocycleId = to,
                    createdAt = stamp,
                    updatedAt = stamp,
                )
                routines.routineExercises(routine.id).executeAsList().forEach { exercise ->
                    routines.upsertRoutineExercise(
                        id = Ids.newId(),
                        routineId = copyId,
                        exerciseId = exercise.exercise_id,
                        position = exercise.position,
                        targetSets = exercise.target_sets,
                        targetReps = exercise.target_reps,
                        targetWeightKg = exercise.target_weight_kg,
                        targetDurationS = exercise.target_duration_s,
                        restS = exercise.rest_s,
                        now = stamp,
                    )
                }
            }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private fun nameFor(number: Int) = "Mesocycle $number"

    private fun toMesocycle(id: String, name: String, startDate: String, endDate: String) =
        Mesocycle(id = id, name = name, start = LocalDate.parse(startDate), end = LocalDate.parse(endDate))
}
