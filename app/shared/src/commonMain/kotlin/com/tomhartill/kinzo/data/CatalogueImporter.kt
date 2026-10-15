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
            SOURCES.forEach { source ->
                queries.softDeleteExercisesFromSource(now = now, source = source)
                queries.softDeleteEquipmentFromSource(now = now, source = source)
                queries.deleteMusclesFromSource(source = source)
                queries.softDeleteGroupsFromSource(now = now, source = source)
                queries.deleteMachineExercisesFromSource(source = source)
                queries.softDeleteMachinesFromSource(now = now, source = source)
            }
            file.groups.forEach { group ->
                queries.upsertGroup(id = group.id, name = group.name, icon = group.icon, source = KINZO_SOURCE, updatedAt = now)
            }
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
                    source = exercise.source ?: SOURCE,
                    sourceRef = exercise.sourceRef,
                    icon = exercise.icon,
                    updatedAt = now,
                    groupId = exercise.groupId,
                )
                exercise.primaryMuscles.forEach { queries.insertMuscle(exerciseId = exercise.id, muscle = it, isPrimary = 1) }
                exercise.secondaryMuscles.forEach { queries.insertMuscle(exerciseId = exercise.id, muscle = it, isPrimary = 0) }
            }
            file.machines.forEach { machine ->
                queries.upsertMachine(
                    id = machine.id,
                    name = machine.name,
                    equipmentId = machine.equipmentId,
                    aliases = machine.aliases.joinToString("\n"),
                    description = machine.description,
                    source = KINZO_SOURCE,
                    updatedAt = now,
                )
                machine.exerciseIds.forEachIndexed { position, exerciseId ->
                    queries.insertMachineExercise(machineId = machine.id, exerciseId = exerciseId, position = position.toLong())
                }
            }
            db.settingQueries.putSetting(key = VERSION_KEY, value = file.version.toString())
        }
    }

    companion object {
        const val SOURCE = "free-exercise-db"

        /** Kinzo's own machines, machine exercises and groups. */
        const val KINZO_SOURCE = "kinzo"
        private val SOURCES = listOf(SOURCE, KINZO_SOURCE)
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
    val groups: List<CatalogueGroup> = emptyList(),
    val machines: List<CatalogueMachine> = emptyList(),
)

@Serializable
internal data class CatalogueGroup(val id: String, val name: String, val icon: String? = null)

@Serializable
internal data class CatalogueMachine(
    val id: String,
    val name: String,
    val equipmentId: String? = null,
    val aliases: List<String> = emptyList(),
    val description: String? = null,
    val exerciseIds: List<String> = emptyList(),
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
    /** Null for free-exercise-db rows; "kinzo" for Kinzo's own. */
    val source: String? = null,
    val groupId: String? = null,
)
