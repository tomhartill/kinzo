package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.RoutineSummary
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Saved workouts to start from: the user's own and the ready-made ones bundled with the app.
 * (The code says routine, as the server does, to keep them apart from logged workouts.)
 */
class RoutineRepository(
    private val db: KinzoDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val queries get() = db.routineQueries

    private fun now(): Long = clock.now().toEpochMilliseconds()

    /**
     * Every saved workout. The user's own come first, most recently used or changed first,
     * then the ready-made ones in their bundled order.
     */
    fun routines(): Flow<List<RoutineSummary>> = combine(
        queries.routines().asFlow().mapToList(io),
        queries.routineExerciseNames().asFlow().mapToList(io),
    ) { rows, names ->
        val namesByRoutine = names.groupBy({ it.routine_id }, { it.name })
        val (own, others) = rows.partition { RoutineSource.fromKey(it.created_by) == RoutineSource.User }
        val ordered = own.sortedByDescending { maxOf(it.last_used_at ?: 0, it.updated_at) } + others.sortedBy { it.created_at }
        ordered
            .map { row ->
                RoutineSummary(
                    id = row.id,
                    name = row.name,
                    notes = row.notes,
                    source = RoutineSource.fromKey(row.created_by),
                    exerciseNames = namesByRoutine[row.id].orEmpty(),
                    lastUsedAt = row.last_used_at,
                    type = WorkoutType.fromKey(row.workout_type),
                    splitName = row.split_name,
                    mesocycleId = row.mesocycle_id,
                )
            }
    }.distinctUntilChanged()

    /** One saved workout with its exercises. Emits null once it is deleted. */
    fun routine(id: String): Flow<Routine?> = combine(
        queries.routineById(id).asFlow().mapToOneOrNull(io),
        queries.routineExercises(id).asFlow().mapToList(io),
    ) { header, exercises ->
        header ?: return@combine null
        Routine(
            id = header.id,
            name = header.name,
            notes = header.notes,
            source = RoutineSource.fromKey(header.created_by),
            exercises = exercises.map { row ->
                RoutineExercise(
                    id = row.id,
                    exerciseId = row.exercise_id,
                    name = row.name,
                    measurementType = MeasurementType.fromKey(row.measurement_type),
                    targetSets = row.target_sets.toInt(),
                    target = SetValues(
                        reps = row.target_reps?.toInt(),
                        weightKg = row.target_weight_kg,
                        durationS = row.target_duration_s?.toInt(),
                    ),
                    restSeconds = row.rest_s?.toInt(),
                )
            },
            type = WorkoutType.fromKey(header.workout_type),
            splitId = header.split_id,
            splitName = header.split_name,
            mesocycleId = header.mesocycle_id,
        )
    }.distinctUntilChanged()

    /**
     * Saves one of the user's workouts, creating it if [routine]'s ID is new. Its exercises
     * become exactly [Routine.exercises], in that order. Without a mesocycle it goes into the
     * current one. Ready-made workouts can't be saved over (save a [Routine.copyAsNew]
     * instead); returns false and changes nothing for those.
     */
    suspend fun save(routine: Routine): Boolean = withContext(io) {
        db.transactionWithResult {
            val now = now()
            val name = routine.name.trim().ifEmpty { UNTITLED }
            val notes = routine.notes?.trim()?.ifEmpty { null }
            val today = clock.now().toLocalDateTime(zone()).date.toString()
            val mesocycleId = routine.mesocycleId ?: db.mesocycleQueries.currentMesocycle(today).executeAsOneOrNull()?.id
            val existing = queries.routineById(routine.id).executeAsOneOrNull()
            when {
                existing == null -> queries.upsertRoutine(
                    id = routine.id,
                    name = name,
                    notes = notes,
                    createdBy = RoutineSource.User.key,
                    workoutType = routine.type.key,
                    splitId = routine.splitId,
                    mesocycleId = mesocycleId,
                    createdAt = now,
                    updatedAt = now,
                )
                RoutineSource.fromKey(existing.created_by) != RoutineSource.User -> return@transactionWithResult false
                else -> queries.updateRoutine(
                    name = name,
                    notes = notes,
                    workoutType = routine.type.key,
                    splitId = routine.splitId,
                    mesocycleId = mesocycleId,
                    now = now,
                    id = routine.id,
                )
            }
            writeExercises(routine.id, routine.exercises, now)
            true
        }
    }

    /** Deletes one of the user's workouts. Workouts already logged from it are kept. */
    suspend fun delete(routineId: String) = withContext(io) {
        db.transaction {
            val existing = queries.routineById(routineId).executeAsOneOrNull() ?: return@transaction
            if (RoutineSource.fromKey(existing.created_by) != RoutineSource.User) return@transaction
            val now = now()
            queries.softDeleteExercisesOfRoutine(now = now, routineId = routineId)
            queries.softDeleteRoutine(now = now, id = routineId)
        }
    }

    private fun writeExercises(routineId: String, exercises: List<RoutineExercise>, now: Long) {
        val keep = exercises.map { it.id }.toSet()
        queries.routineExerciseIds(routineId).executeAsList()
            .filterNot { it in keep }
            .forEach { queries.softDeleteRoutineExercise(now = now, id = it) }
        exercises.forEachIndexed { index, exercise ->
            val target = exercise.target.keepOnly(exercise.measurementType)
            queries.upsertRoutineExercise(
                id = exercise.id,
                routineId = routineId,
                exerciseId = exercise.exerciseId,
                position = index.toLong(),
                targetSets = exercise.targetSets.coerceIn(RoutineExercise.MIN_SETS, RoutineExercise.MAX_SETS).toLong(),
                targetReps = target.reps?.takeIf { it > 0 }?.toLong(),
                targetWeightKg = target.weightKg?.takeIf { it >= 0 }?.let(SetRules::roundKg),
                targetDurationS = target.durationS?.takeIf { it > 0 }?.toLong(),
                restS = exercise.restSeconds?.coerceIn(0, Settings.MAX_REST_SECONDS)?.toLong(),
                now = now,
            )
        }
    }

    companion object {
        const val UNTITLED = "Untitled workout"
    }
}
