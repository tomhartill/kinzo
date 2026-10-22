package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.TrendDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val workouts = WorkoutRepository(db, clock, Dispatchers.Unconfined)
    private val trends = TrendRepository(db, clock, Dispatchers.Unconfined)

    @BeforeTest
    fun seed() = runTest {
        CatalogueImporter(db, clock, Dispatchers.Unconfined) { smallCatalogue() }.importIfNeeded()
    }

    /** Logs and finishes one set of bench press at [weightKg] for one rep. */
    private suspend fun logBench(weightKg: Double): String {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench"))
        val set = assertNotNull(workouts.activeWorkout().first()).exercises.single().sets.single()
        workouts.completeSet(set.id, SetValues(weightKg = weightKg, reps = 1))
        clock.advance(60 * 60 * 1000)
        assertTrue(workouts.finishWorkout(id))
        clock.advance(24 * 60 * 60 * 1000)
        return id
    }

    @Test
    fun finishingWorkoutsKeepsTheTrendCurrent() = runTest {
        logBench(100.0)
        assertNull(trends.exerciseTrends().first()["ex-bench"], "one session isn't a line")
        logBench(105.0)
        logBench(110.0)
        val trend = assertNotNull(trends.exerciseTrends().first()["ex-bench"])
        assertEquals(listOf(100.0, 105.0, 110.0), trend.values)
        assertEquals(TrendDirection.Up, trend.direction)
    }

    @Test
    fun deletingAWorkoutTakesItOutOfTheTrend() = runTest {
        logBench(100.0)
        logBench(105.0)
        val last = logBench(90.0)
        assertEquals(TrendDirection.Down, trends.exerciseTrends().first().getValue("ex-bench").direction)
        workouts.deleteWorkout(last)
        assertEquals(listOf(100.0, 105.0), trends.exerciseTrends().first().getValue("ex-bench").values)
    }

    @Test
    fun aWorkoutInProgressDoesNotCount() = runTest {
        logBench(100.0)
        logBench(105.0)
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench"))
        assertEquals(listOf(100.0, 105.0), trends.exerciseTrends().first().getValue("ex-bench").values)
    }

    @Test
    fun historyFromBeforeTheCacheIsBuiltOnce() = runTest {
        logBench(100.0)
        logBench(110.0)
        // As if these were logged by a version without trends.
        db.trendQueries.deleteTrendsOfKind("exercise")
        assertTrue(trends.exerciseTrends().first().isEmpty())

        trends.rebuildIfNeeded()
        assertEquals(listOf(100.0, 110.0), trends.exerciseTrends().first().getValue("ex-bench").values)

        // Already built for this version, so a second call leaves the table alone.
        db.trendQueries.deleteTrendsOfKind("exercise")
        trends.rebuildIfNeeded()
        assertTrue(trends.exerciseTrends().first().isEmpty())
    }
}
