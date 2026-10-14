package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.db.KinzoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/**
 * Copies the catalogue bundled with the app (built by tools/catalogue) into the local
 * database. It runs on every launch but only imports when the bundled version differs from
 * the one already imported, so normally it costs one small read.
 */
class CatalogueImporter(
    private val db: KinzoDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val readCatalogue: suspend () -> String,
) {
    /** Returns true when it imported a new catalogue version. */
    suspend fun importIfNeeded(): Boolean = withContext(io) {
        val text = readCatalogue()
        val bundledVersion = VERSION_PATTERN.find(text.take(100))?.groupValues?.get(1)
        val importedVersion = db.settingQueries.getSetting(VERSION_KEY).executeAsOneOrNull()
        if (bundledVersion != null && bundledVersion == importedVersion) return@withContext false
        import(json.decodeFromString(CatalogueFile.serializer(), text))
        true
    }

    private fun import(file: CatalogueFile) {
        val now = clock.now().toEpochMilliseconds()
        val queries = db.catalogueQueries
        db.transaction {
            // Anything the new version dropped stays deleted; everything else is revived below.
            queries.softDeleteExercisesFromSource(now = now, source = SOURCE)
            queries.softDeleteEquipmentFromSource(now = now, source = SOURCE)
            queries.deleteMusclesFromSource(source = SOURCE)
            file.equipment.forEach { equipment ->
                queries.upsertEquipment(
                    id = equipment.id,
                    name = equipment.name,
                    type = equipment.type,
                    source = SOURCE,
                    updatedAt = now,
                )
            }
            file.exercises.forEach { exercise ->
                queries.upsertExercise(
                    id = exercise.id,
                    name = exercise.name,
                    instructions = exercise.instructions.joinToString("\n"),
                    equipmentId = exercise.equipmentId,
                    measurementType = exercise.measurementType,
                    source = SOURCE,
                    sourceRef = exercise.sourceRef,
                    icon = exercise.icon,
                    updatedAt = now,
                )
                exercise.primaryMuscles.forEach { queries.insertMuscle(exerciseId = exercise.id, muscle = it, isPrimary = 1) }
                exercise.secondaryMuscles.forEach { queries.insertMuscle(exerciseId = exercise.id, muscle = it, isPrimary = 0) }
            }
            db.settingQueries.putSetting(key = VERSION_KEY, value = file.version.toString())
        }
    }

    companion object {
        const val SOURCE = "free-exercise-db"
        const val RESOURCE_PATH = "files/catalogue.json"
        internal const val VERSION_KEY = "catalogue_version"
        private val VERSION_PATTERN = Regex(""""version"\s*:\s*(\d+)""")
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class CatalogueFile(
    val version: Int,
    val equipment: List<CatalogueEquipment>,
    val exercises: List<CatalogueExercise>,
)

@Serializable
internal data class CatalogueEquipment(val id: String, val name: String, val type: String)

@Serializable
internal data class CatalogueExercise(
    val id: String,
    val name: String,
    val equipmentId: String? = null,
    val measurementType: String,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val sourceRef: String? = null,
    val icon: String? = null,
)
