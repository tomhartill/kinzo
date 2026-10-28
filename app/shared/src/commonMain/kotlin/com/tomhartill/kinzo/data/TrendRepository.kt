package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.ExerciseSession
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.PreviousSet
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Trend
import com.tomhartill.kinzo.domain.Trends
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Writes the cached trend rows. Its functions run inside the caller's transaction, so a
 * finished or deleted workout and the trends it changes are saved together.
 */
internal class TrendCache(private val db: KinzoDatabase, private val clock: Clock) {
    private val queries get() = db.trendQueries

    /** Recomputes the trends of these exercises from their latest sessions. */
    fun refreshExercises(exerciseIds: Collection<String>) {
        val now = clock.now().toEpochMilliseconds()
        exerciseIds.distinct().forEach { exerciseId ->
            val type = queries.exerciseMeasurementType(exerciseId).executeAsOneOrNull()?.let { MeasurementType.fromKey(it) }
            val values = type?.let { Trends.exerciseValues(it, recentSessions(exerciseId)) }.orEmpty()
            if (type == null || values.isEmpty()) {
                queries.deleteTrend(kind = EXERCISE, subjectId = exerciseId)
            } else {
                queries.upsertTrend(
                    kind = EXERCISE,
                    subjectId = exerciseId,
                    metric = Trends.metricFor(type).name,
                    recentValues = Trends.encode(values),
                    now = now,
                )
            }
        }
    }

    /** Throws away every exercise trend and builds them again from history. */
    fun rebuildExercises() {
        queries.deleteTrendsOfKind(EXERCISE)
        refreshExercises(queries.exercisesWithHistory().executeAsList())
    }

    private fun recentSessions(exerciseId: String): List<ExerciseSession> =
        queries.recentExerciseSets(exerciseId, Trends.SESSIONS.toLong()).executeAsList()
            .groupBy { it.workout_id }
            .map { (workoutId, sets) ->
                ExerciseSession(
                    workoutId = workoutId,
                    workoutName = "",
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

    companion object {
        const val EXERCISE = "exercise"
    }
}

/**
 * Sparkline trends for lists. Reading is one small table, whatever the length of the history;
 * [WorkoutRepository] keeps it current as workouts finish or are deleted.
 */
class TrendRepository(
    private val db: KinzoDatabase,
    clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val cache = TrendCache(db, clock)

    /** Each exercise's trend by exercise ID. Exercises with fewer than two sessions have none. */
    fun exerciseTrends(): Flow<Map<String, Trend>> =
        db.trendQueries.trendsOfKind(TrendCache.EXERCISE).asFlow().mapToList(io).map { rows ->
            buildMap {
                rows.forEach { row -> Trends.of(Trends.decode(row.recent_values))?.let { put(row.subject_id, it) } }
            }
        }

    /**
     * Builds the cache from history the first time this version runs, and again whenever the
     * trend maths changes ([Trends.VERSION]). Afterwards it costs one settings read.
     */
    suspend fun rebuildIfNeeded() = withContext(io) {
        val wanted = Trends.VERSION.toString()
        if (db.settingQueries.getSetting(VERSION_KEY).executeAsOneOrNull() == wanted) return@withContext
        db.transaction {
            cache.rebuildExercises()
            db.settingQueries.putSetting(key = VERSION_KEY, value = wanted)
        }
    }

    private companion object {
        const val VERSION_KEY = "trend_version"
    }
}
