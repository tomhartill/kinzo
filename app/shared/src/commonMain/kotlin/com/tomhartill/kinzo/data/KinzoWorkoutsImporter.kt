package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.RoutineSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Copies the ready-made workouts bundled with the app (built by tools/catalogue) into the
 * local database, alongside the user's own. Like [CatalogueImporter], it only imports when the
 * bundled version differs from the one already imported. Run it after the catalogue, whose
 * exercises these refer to.
 */
class KinzoWorkoutsImporter(
    private val db: KinzoDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val readWorkouts: suspend () -> String,
) {
    /** Returns true when it imported a new version. */
    suspend fun importIfNeeded(): Boolean = withContext(io) {
        val text = readWorkouts()
        val bundledVersion = VERSION_PATTERN.find(text.take(100))?.groupValues?.get(1)
        val importedVersion = db.settingQueries.getSetting(VERSION_KEY).executeAsOneOrNull()
        if (bundledVersion != null && bundledVersion == importedVersion) return@withContext false
        import(json.decodeFromString(WorkoutsFile.serializer(), text))
        true
    }

    private fun import(file: WorkoutsFile) {
        val now = clock.now().toEpochMilliseconds()
        val queries = db.routineQueries
        val source = RoutineSource.Kinzo.key
        db.transaction {
            // Anything the new version dropped stays deleted; everything else is revived below.
            queries.softDeleteExercisesOfRoutinesBy(now = now, createdBy = source)
            queries.softDeleteRoutinesBy(now = now, createdBy = source)
            file.workouts.forEachIndexed { index, workout ->
                queries.upsertRoutine(
                    id = workout.id,
                    name = workout.name,
                    notes = workout.notes,
                    createdBy = source,
                    // Keeps the bundled order in lists.
                    createdAt = index.toLong(),
                    updatedAt = now,
                )
                workout.exercises.forEachIndexed { position, exercise ->
                    queries.upsertRoutineExercise(
                        id = exercise.id,
                        routineId = workout.id,
                        exerciseId = exercise.exerciseId,
                        position = position.toLong(),
                        targetSets = exercise.sets.toLong(),
                        targetReps = exercise.reps?.toLong(),
                        targetWeightKg = null,
                        targetDurationS = exercise.durationS?.toLong(),
                        restS = exercise.restS?.toLong(),
                        now = now,
                    )
                }
            }
            db.settingQueries.putSetting(key = VERSION_KEY, value = file.version.toString())
        }
    }

    companion object {
        const val RESOURCE_PATH = "files/workouts.json"
        internal const val VERSION_KEY = "kinzo_workouts_version"
        private val VERSION_PATTERN = Regex(""""version"\s*:\s*(\d+)""")
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class WorkoutsFile(val version: Int, val workouts: List<BundledWorkout>)

@Serializable
internal data class BundledWorkout(
    val id: String,
    val name: String,
    val notes: String? = null,
    val exercises: List<BundledWorkoutExercise>,
)

@Serializable
internal data class BundledWorkoutExercise(
    val id: String,
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val durationS: Int? = null,
    val restS: Int? = null,
)
