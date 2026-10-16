package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.Equipment
import com.tomhartill.kinzo.domain.ExerciseDetail
import com.tomhartill.kinzo.domain.ExerciseSummary
import com.tomhartill.kinzo.domain.Machine
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Read access to exercises and equipment. */
class CatalogueRepository(
    private val db: KinzoDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.catalogueQueries

    fun exercises(): Flow<List<ExerciseSummary>> =
        queries.exerciseList().asFlow().mapToList(io).map { rows ->
            rows.map { row ->
                ExerciseSummary(
                    id = row.id,
                    name = row.name,
                    measurementType = MeasurementType.fromKey(row.measurement_type),
                    equipmentId = row.equipment_id,
                    equipmentName = row.equipment_name,
                    primaryMuscles = Muscle.parseList(row.primary_muscles),
                    secondaryMuscles = Muscle.parseList(row.secondary_muscles),
                    icon = row.icon,
                    groupId = row.group_id,
                    groupName = row.group_name,
                    groupIcon = row.group_icon,
                )
            }
        }

    fun exercise(id: String): Flow<ExerciseDetail?> = combine(
        queries.exerciseById(id).asFlow().mapToOneOrNull(io),
        queries.musclesForExercise(id).asFlow().mapToList(io),
    ) { row, muscles ->
        row?.let {
            ExerciseDetail(
                id = it.id,
                name = it.name,
                description = it.description,
                instructions = it.instructions.lines().map(String::trim).filter(String::isNotEmpty),
                measurementType = MeasurementType.fromKey(it.measurement_type),
                equipmentName = it.equipment_name,
                primaryMuscles = muscles.filter { m -> m.is_primary == 1L }.mapNotNull { m -> Muscle.fromKey(m.muscle) },
                secondaryMuscles = muscles.filter { m -> m.is_primary == 0L }.mapNotNull { m -> Muscle.fromKey(m.muscle) },
                icon = it.icon,
                groupName = it.group_name,
            )
        }
    }

    /** Every named machine, for the machine scan's results and its "not found" list. */
    suspend fun machines(): List<Machine> = withContext(io) {
        queries.machineList().executeAsList().map { row ->
            Machine(
                id = row.id,
                name = row.name,
                aliases = row.aliases.lines().filter(String::isNotBlank),
                description = row.description,
                exerciseIds = queries.machineExerciseIds(row.id).executeAsList(),
            )
        }
    }

    fun equipment(): Flow<List<Equipment>> =
        queries.equipmentList().asFlow().mapToList(io).map { rows ->
            rows.map { Equipment(id = it.id, name = it.name, type = it.type) }
        }
}
