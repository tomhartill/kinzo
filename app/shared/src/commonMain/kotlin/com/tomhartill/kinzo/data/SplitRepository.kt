package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.db.Splits
import com.tomhartill.kinzo.domain.Ids
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.Split
import com.tomhartill.kinzo.domain.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Splits to file saved workouts under: the built-in ones and the user's own. */
class SplitRepository(
    private val db: KinzoDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.splitQueries

    /** Every split, the built-in ones first in their bundled order, then the user's own as added. */
    fun splits(): Flow<List<Split>> =
        queries.splits().asFlow().mapToList(io).map { rows -> rows.map { it.toSplit() } }.distinctUntilChanged()

    /**
     * Adds a split of the user's own for [type], or returns the existing one if there's already
     * a split of that name for that type. Returns null for a blank name.
     */
    suspend fun add(name: String, type: WorkoutType): Split? = withContext(io) {
        val cleaned = name.trim().replace(WHITESPACE, " ").take(MAX_NAME_LENGTH).trim()
        if (cleaned.isEmpty()) return@withContext null
        db.transactionWithResult {
            val existing = queries.splits().executeAsList()
                .firstOrNull { it.workout_type == type.key && it.name.equals(cleaned, ignoreCase = true) }
            if (existing != null) return@transactionWithResult existing.toSplit()
            val now = clock.now().toEpochMilliseconds()
            val split = Split(id = Ids.newId(), name = cleaned, type = type, isBuiltIn = false)
            queries.upsertSplit(
                id = split.id,
                name = split.name,
                workoutType = type.key,
                createdBy = RoutineSource.User.key,
                createdAt = now,
                updatedAt = now,
            )
            split
        }
    }

    /** Removes one of the user's splits. Workouts filed under it keep its name. Built-in ones stay. */
    suspend fun delete(id: String) = withContext(io) {
        queries.softDeleteUserSplit(now = clock.now().toEpochMilliseconds(), id = id)
    }

    private fun Splits.toSplit() = Split(
        id = id,
        name = name,
        type = WorkoutType.fromKey(workout_type),
        isBuiltIn = created_by != RoutineSource.User.key,
    )

    companion object {
        const val MAX_NAME_LENGTH = 40
        private val WHITESPACE = Regex("""\s+""")
    }
}
