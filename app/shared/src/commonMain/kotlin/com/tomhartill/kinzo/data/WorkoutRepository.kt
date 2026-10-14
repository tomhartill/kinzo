package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.ActiveWorkoutInfo
import com.tomhartill.kinzo.domain.ExerciseLine
import com.tomhartill.kinzo.domain.ExerciseSession
import com.tomhartill.kinzo.domain.Ids
import com.tomhartill.kinzo.domain.LoggedSet
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.PreviousSet
import com.tomhartill.kinzo.domain.SetRules
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.Workout
import com.tomhartill.kinzo.domain.WorkoutExercise
import com.tomhartill.kinzo.domain.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Workouts, their exercises and sets. Reads are flows that update whenever the tables
 * change; writes run on the IO dispatcher inside transactions.
 */
class WorkoutRepository(
    private val db: KinzoDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.workoutQueries

    private fun now(): Long = clock.now().toEpochMilliseconds()

    // Reads

    /** The workout in progress without its sets, or null. Cheaper than [activeWorkout]. */
    fun activeWorkoutInfo(): Flow<ActiveWorkoutInfo?> =
        queries.activeWorkoutInfo().asFlow().mapToOneOrNull(io).map { row ->
            row?.let { ActiveWorkoutInfo(id = it.id, name = it.name, startedAt = it.started_at) }
        }.distinctUntilChanged()

    /** The workout in progress, if any, with last time's sets for each exercise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun activeWorkout(): Flow<Workout?> =
        queries.activeWorkoutId().asFlow().mapToOneOrNull(io)
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else workout(id, withPrevious = true) }

    /** One workout with its exercises and sets. Emits null once it is deleted. */
    fun workout(id: String, withPrevious: Boolean = false): Flow<Workout?> = flow {
        // Last time's sets don't change during a workout, so each exercise's are read once.
        val previousByExercise = mutableMapOf<String, List<PreviousSet>>()
        emitAll(
            combine(
                queries.workoutById(id).asFlow().mapToOneOrNull(io),
                queries.workoutExercises(id).asFlow().mapToList(io),
                queries.workoutSets(id).asFlow().mapToList(io),
            ) { header, exercises, sets ->
                if (header == null) return@combine null
                val setsByExercise = sets.groupBy { it.workout_exercise_id }
                Workout(
                    id = header.id,
                    name = header.name,
                    startedAt = header.started_at,
                    endedAt = header.ended_at,
                    exercises = exercises.map { exercise ->
                        WorkoutExercise(
                            id = exercise.id,
                            exerciseId = exercise.exercise_id,
                            name = exercise.name,
                            measurementType = MeasurementType.fromKey(exercise.measurement_type),
                            sets = setsByExercise[exercise.id].orEmpty().map { set ->
                                LoggedSet(
                                    id = set.id,
                                    values = SetValues(
                                        reps = set.reps?.toInt(),
                                        weightKg = set.weight_kg,
                                        durationS = set.duration_s?.toInt(),
                                        distanceM = set.distance_m,
                                    ),
                                    isWarmup = set.is_warmup != 0L,
                                    completedAt = set.completed_at,
                                )
                            },
                            previous = if (withPrevious) {
                                previousByExercise.getOrPut(exercise.exercise_id) { previousSets(exercise.exercise_id, id) }
                            } else {
                                emptyList()
                            },
                            restSeconds = exercise.rest_s?.toInt(),
                        )
                    },
                )
            },
        )
    }.flowOn(io)

    /** Finished workouts, newest first, with totals and the exercises in each. */
    fun history(): Flow<List<WorkoutSummary>> = combine(
        queries.finishedWorkouts().asFlow().mapToList(io),
        queries.finishedWorkoutExercises().asFlow().mapToList(io),
    ) { workouts, lines ->
        val linesByWorkout = lines.groupBy { it.workout_id }
        workouts.map { workout ->
            WorkoutSummary(
                id = workout.id,
                name = workout.name,
                startedAt = workout.started_at,
                endedAt = workout.ended_at,
                setCount = workout.set_count.toInt(),
                volumeKg = workout.volume_kg,
                exercises = linesByWorkout[workout.id].orEmpty().map { ExerciseLine(it.name, it.set_count.toInt()) },
            )
        }
    }

    /** Every finished session of one exercise, newest first. */
    fun exerciseHistory(exerciseId: String): Flow<List<ExerciseSession>> =
        queries.exerciseHistory(exerciseId).asFlow().mapToList(io).map { rows ->
            rows.groupBy { it.workout_id }.map { (workoutId, sets) ->
                ExerciseSession(
                    workoutId = workoutId,
                    workoutName = sets.first().workout_name,
                    startedAt = sets.first().started_at,
                    sets = sets.map { set ->
                        PreviousSet(
                            values = SetValues(
                                reps = set.reps?.toInt(),
                                weightKg = set.weight_kg,
                                durationS = set.duration_s?.toInt(),
                                distanceM = set.distance_m,
                            ),
                            isWarmup = set.is_warmup != 0L,
                        )
                    },
                )
            }
        }

    /** Exercises from recent finished workouts, most recent first. */
    fun recentExerciseIds(limit: Int = 12): Flow<List<String>> =
        queries.recentExerciseIds(limit.toLong()).asFlow().mapToList(io)

    private fun previousSets(exerciseId: String, excludeWorkoutId: String): List<PreviousSet> =
        queries.previousSets(exerciseId = exerciseId, excludeWorkoutId = excludeWorkoutId).executeAsList().map { set ->
            PreviousSet(
                values = SetValues(
                    reps = set.reps?.toInt(),
                    weightKg = set.weight_kg,
                    durationS = set.duration_s?.toInt(),
                    distanceM = set.distance_m,
                ),
                isWarmup = set.is_warmup != 0L,
            )
        }

    // Writes

    suspend fun activeWorkoutId(): String? = withContext(io) { queries.activeWorkoutId().executeAsOneOrNull() }

    /** Starts a workout, or returns the one already in progress. Returns its ID. */
    suspend fun startWorkout(name: String): String = withContext(io) {
        db.transactionWithResult {
            queries.activeWorkoutId().executeAsOneOrNull()
                ?: Ids.newId().also { id -> queries.insertWorkout(id = id, name = name, routineId = null, startedAt = now()) }
        }
    }

    /** What happened when asked to start a workout from a saved one. */
    sealed interface StartResult {
        data class Started(val workoutId: String) : StartResult

        /** Another workout is still in progress; finish or discard it first. */
        data class AlreadyInProgress(val workoutId: String) : StartResult

        /** The saved workout is gone. */
        data object NotFound : StartResult
    }

    /**
     * Starts a workout from a saved one: its name, exercises in order, the planned number of
     * sets, and its rest times. Each set comes filled with the planned numbers where there
     * are any; the rest are left for the user, with last time's numbers as hints.
     */
    suspend fun startFromRoutine(routineId: String): StartResult = withContext(io) {
        db.transactionWithResult {
            queries.activeWorkoutId().executeAsOneOrNull()?.let { return@transactionWithResult StartResult.AlreadyInProgress(it) }
            val routine = db.routineQueries.routineById(routineId).executeAsOneOrNull()
                ?: return@transactionWithResult StartResult.NotFound
            val now = now()
            val workoutId = Ids.newId()
            queries.insertWorkout(id = workoutId, name = routine.name, routineId = routineId, startedAt = now)
            db.routineQueries.routineExercises(routineId).executeAsList().forEachIndexed { position, planned ->
                val workoutExerciseId = Ids.newId()
                queries.insertWorkoutExercise(
                    id = workoutExerciseId,
                    workoutId = workoutId,
                    exerciseId = planned.exercise_id,
                    position = position.toLong(),
                    // As planned. No rest planned means the default, which is what the plan shows.
                    restS = planned.rest_s,
                    now = now,
                )
                val type = MeasurementType.fromKey(planned.measurement_type)
                val values = SetValues(
                    reps = planned.target_reps?.toInt(),
                    weightKg = planned.target_weight_kg,
                    durationS = planned.target_duration_s?.toInt(),
                ).keepOnly(type)
                repeat(planned.target_sets.toInt().coerceAtLeast(1)) { index ->
                    insertSet(workoutExerciseId = workoutExerciseId, position = index, isWarmup = false, now = now, values = values)
                }
            }
            StartResult.Started(workoutId)
        }
    }

    suspend fun renameWorkout(workoutId: String, name: String) = withContext(io) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) queries.renameWorkout(name = trimmed, now = now(), id = workoutId)
    }

    /**
     * Appends exercises to a workout. Each starts with as many sets as it had last time
     * (at least one), with last time's warm-ups marked, so the user only fills in numbers,
     * and with the rest time last chosen for it.
     */
    suspend fun addExercises(workoutId: String, exerciseIds: List<String>) = withContext(io) {
        db.transaction {
            val now = now()
            var position = queries.workoutExerciseIds(workoutId).executeAsList().size
            exerciseIds.forEach { exerciseId ->
                val workoutExerciseId = Ids.newId()
                queries.insertWorkoutExercise(
                    id = workoutExerciseId,
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    position = position.toLong(),
                    restS = queries.lastRestForExercise(exerciseId).executeAsOneOrNull(),
                    now = now,
                )
                position++
                val previous = previousSets(exerciseId, workoutId)
                val count = previous.size.coerceIn(SetRules.MIN_INITIAL_SETS, SetRules.MAX_INITIAL_SETS)
                repeat(count) { index ->
                    insertSet(
                        workoutExerciseId = workoutExerciseId,
                        position = index,
                        isWarmup = previous.getOrNull(index)?.isWarmup == true,
                        now = now,
                    )
                }
            }
        }
    }

    suspend fun addSet(workoutExerciseId: String) = withContext(io) {
        db.transaction {
            val position = queries.setIdsOfWorkoutExercise(workoutExerciseId).executeAsList().size
            insertSet(workoutExerciseId = workoutExerciseId, position = position, isWarmup = false, now = now())
        }
    }

    /** Saves what the user typed, without changing whether the set is done. */
    suspend fun updateSet(setId: String, values: SetValues) = withContext(io) {
        queries.updateSetValues(
            reps = values.reps?.toLong(),
            weightKg = values.weightKg?.let(SetRules::roundKg),
            durationS = values.durationS?.toLong(),
            distanceM = values.distanceM,
            now = now(),
            id = setId,
        )
    }

    suspend fun completeSet(setId: String, values: SetValues) = withContext(io) {
        queries.completeSet(
            reps = values.reps?.toLong(),
            weightKg = values.weightKg?.let(SetRules::roundKg),
            durationS = values.durationS?.toLong(),
            distanceM = values.distanceM,
            now = now(),
            id = setId,
        )
    }

    suspend fun uncompleteSet(setId: String) = withContext(io) {
        queries.uncompleteSet(now = now(), id = setId)
    }

    suspend fun setWarmup(setId: String, isWarmup: Boolean) = withContext(io) {
        queries.setWarmup(isWarmup = if (isWarmup) 1L else 0L, now = now(), id = setId)
    }

    suspend fun removeSet(setId: String) = withContext(io) {
        db.transaction {
            val set = queries.setById(setId).executeAsOneOrNull() ?: return@transaction
            val now = now()
            queries.softDeleteSet(now = now, id = setId)
            renumberSets(set.workout_exercise_id, now)
        }
    }

    /** Puts back a set deleted by [removeSet], in the place it was. */
    suspend fun restoreSet(setId: String) = withContext(io) {
        db.transaction {
            val set = queries.anySetById(setId).executeAsOneOrNull() ?: return@transaction
            if (set.deleted_at == null) return@transaction
            // Nothing to go back to if its exercise was removed in the meantime.
            queries.workoutExerciseById(set.workout_exercise_id).executeAsOneOrNull() ?: return@transaction
            val ids = queries.setIdsOfWorkoutExercise(set.workout_exercise_id).executeAsList().toMutableList()
            ids.add(set.position.toInt().coerceIn(0, ids.size), setId)
            val now = now()
            queries.undeleteSet(now = now, id = setId)
            ids.forEachIndexed { index, id -> queries.setSetPosition(position = index.toLong(), now = now, id = id) }
        }
    }

    /** Sets the rest after each set of one exercise: seconds, 0 for none, or null for the default. */
    suspend fun setExerciseRest(workoutExerciseId: String, seconds: Int?) = withContext(io) {
        queries.setWorkoutExerciseRest(
            restS = seconds?.coerceIn(0, Settings.MAX_REST_SECONDS)?.toLong(),
            now = now(),
            id = workoutExerciseId,
        )
    }

    suspend fun removeExercise(workoutExerciseId: String) = withContext(io) {
        db.transaction {
            val exercise = queries.workoutExerciseById(workoutExerciseId).executeAsOneOrNull() ?: return@transaction
            val now = now()
            queries.softDeleteSetsOfWorkoutExercise(now = now, workoutExerciseId = workoutExerciseId)
            queries.softDeleteWorkoutExercise(now = now, id = workoutExerciseId)
            renumberExercises(exercise.workout_id, now)
        }
    }

    /** Moves an exercise up (negative [offset]) or down within its workout. */
    suspend fun moveExercise(workoutExerciseId: String, offset: Int) = withContext(io) {
        db.transaction {
            val exercise = queries.workoutExerciseById(workoutExerciseId).executeAsOneOrNull() ?: return@transaction
            val ids = queries.workoutExerciseIds(exercise.workout_id).executeAsList().toMutableList()
            val from = ids.indexOf(workoutExerciseId)
            val to = (from + offset).coerceIn(0, ids.lastIndex)
            if (from < 0 || from == to) return@transaction
            ids.add(to, ids.removeAt(from))
            val now = now()
            ids.forEachIndexed { index, id -> queries.setWorkoutExercisePosition(position = index.toLong(), now = now, id = id) }
        }
    }

    /**
     * Puts a workout's exercises in the given order. IDs it doesn't know are ignored, and any
     * exercise left out keeps its place after the ones given.
     */
    suspend fun reorderExercises(workoutId: String, orderedIds: List<String>) = withContext(io) {
        db.transaction {
            val current = queries.workoutExerciseIds(workoutId).executeAsList()
            val wanted = orderedIds.distinct().filter { it in current }
            val order = wanted + current.filterNot { it in wanted }
            if (order == current) return@transaction
            val now = now()
            order.forEachIndexed { index, id -> queries.setWorkoutExercisePosition(position = index.toLong(), now = now, id = id) }
        }
    }

    /**
     * Ends the workout. Sets that weren't ticked are dropped, along with any exercise left
     * with no sets. Returns false, and changes nothing, if no set was completed at all.
     */
    suspend fun finishWorkout(workoutId: String): Boolean = withContext(io) {
        db.transactionWithResult {
            if (queries.countCompletedSetsOfWorkout(workoutId).executeAsOne() == 0L) return@transactionWithResult false
            val now = now()
            queries.softDeleteIncompleteSetsOfWorkout(now = now, workoutId = workoutId)
            queries.softDeleteEmptyExercisesOfWorkout(now = now, workoutId = workoutId)
            renumberExercises(workoutId, now)
            queries.workoutExerciseIds(workoutId).executeAsList().forEach { renumberSets(it, now) }
            queries.finishWorkout(endedAt = now, id = workoutId)
            true
        }
    }

    /** Throws away a workout in progress, or deletes a finished one from history. */
    suspend fun deleteWorkout(workoutId: String) = withContext(io) {
        db.transaction {
            val now = now()
            queries.softDeleteAllSetsOfWorkout(now = now, workoutId = workoutId)
            queries.softDeleteAllExercisesOfWorkout(now = now, workoutId = workoutId)
            queries.softDeleteWorkout(now = now, id = workoutId)
        }
    }

    private fun insertSet(workoutExerciseId: String, position: Int, isWarmup: Boolean, now: Long, values: SetValues = SetValues()) {
        queries.insertSet(
            id = Ids.newId(),
            workoutExerciseId = workoutExerciseId,
            position = position.toLong(),
            reps = values.reps?.toLong(),
            weightKg = values.weightKg?.let(SetRules::roundKg),
            durationS = values.durationS?.toLong(),
            distanceM = values.distanceM,
            isWarmup = if (isWarmup) 1L else 0L,
            now = now,
        )
    }

    private fun renumberExercises(workoutId: String, now: Long) {
        queries.workoutExerciseIds(workoutId).executeAsList().forEachIndexed { index, id ->
            queries.setWorkoutExercisePosition(position = index.toLong(), now = now, id = id)
        }
    }

    private fun renumberSets(workoutExerciseId: String, now: Long) {
        queries.setIdsOfWorkoutExercise(workoutExerciseId).executeAsList().forEachIndexed { index, id ->
            queries.setSetPosition(position = index.toLong(), now = now, id = id)
        }
    }
}
