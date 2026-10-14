package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val workouts = WorkoutRepository(db, clock, Dispatchers.Unconfined)

    @BeforeTest
    fun seed() = runTest {
        CatalogueImporter(db, clock, Dispatchers.Unconfined) { smallCatalogue() }.importIfNeeded()
    }

    private suspend fun active(): Workout = assertNotNull(workouts.activeWorkout().first())

    /** Logs and finishes a workout with the given (weight, reps) sets of bench press. */
    private suspend fun logBench(vararg sets: Pair<Double, Int>, warmups: Int = 0): String {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench"))
        val exercise = active().exercises.single()
        repeat(sets.size - exercise.sets.size) { workouts.addSet(exercise.id) }
        active().exercises.single().sets.forEachIndexed { index, set ->
            if (index < warmups) workouts.setWarmup(set.id, true)
            workouts.completeSet(set.id, SetValues(weightKg = sets[index].first, reps = sets[index].second))
        }
        clock.advance(60 * 60 * 1000)
        assertTrue(workouts.finishWorkout(id))
        clock.advance(24 * 60 * 60 * 1000)
        return id
    }

    @Test
    fun onlyOneWorkoutIsInProgressAtATime() = runTest {
        val first = workouts.startWorkout("Push")
        assertEquals(first, workouts.startWorkout("Pull"))
        assertEquals("Push", active().name)
    }

    @Test
    fun activeWorkoutInfoFollowsTheWorkoutInProgress() = runTest {
        assertNull(workouts.activeWorkoutInfo().first())
        val id = workouts.startWorkout("Push")
        val info = assertNotNull(workouts.activeWorkoutInfo().first())
        assertEquals(id, info.id)
        assertEquals("Push", info.name)
        workouts.addExercises(id, listOf("ex-bench"))
        workouts.completeSet(active().exercises.single().sets.single().id, SetValues(weightKg = 60.0, reps = 5))
        assertTrue(workouts.finishWorkout(id))
        assertNull(workouts.activeWorkoutInfo().first())
    }

    @Test
    fun newExercisesStartWithOneSetAndKeepTheirOrder() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench", "ex-plank"))
        val workout = active()
        assertEquals(listOf("Bench Press", "Plank"), workout.exercises.map { it.name })
        assertEquals(listOf(1, 1), workout.exercises.map { it.sets.size })
        assertTrue(workout.exercises.all { it.previous.isEmpty() })
    }

    @Test
    fun setValuesAreSavedAndKilogramsRounded() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench"))
        val set = active().exercises.single().sets.single()
        workouts.updateSet(set.id, SetValues(reps = 5, weightKg = 61.23456))
        assertEquals(SetValues(reps = 5, weightKg = 61.235), active().exercises.single().sets.single().values)
        assertFalse(active().exercises.single().sets.single().isCompleted)

        workouts.completeSet(set.id, SetValues(reps = 5, weightKg = 61.235))
        assertTrue(active().exercises.single().sets.single().isCompleted)
        workouts.uncompleteSet(set.id)
        assertFalse(active().exercises.single().sets.single().isCompleted)
    }

    @Test
    fun finishingNeedsAtLeastOneCompletedSet() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench"))
        assertFalse(workouts.finishWorkout(id))
        assertNotNull(workouts.activeWorkout().first())
    }

    @Test
    fun finishingDropsUntickedSetsAndEmptyExercises() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-plank", "ex-bench"))
        val bench = active().exercises.single { it.name == "Bench Press" }
        workouts.addSet(bench.id)
        workouts.addSet(bench.id)
        val benchSets = active().exercises.single { it.name == "Bench Press" }.sets
        workouts.completeSet(benchSets[0].id, SetValues(reps = 8, weightKg = 60.0))
        workouts.completeSet(benchSets[2].id, SetValues(reps = 6, weightKg = 65.0))

        assertTrue(workouts.finishWorkout(id))
        assertNull(workouts.activeWorkout().first())

        val finished = assertNotNull(workouts.workout(id).first())
        val exercise = finished.exercises.single()
        assertEquals("Bench Press", exercise.name)
        assertEquals(listOf(SetValues(reps = 8, weightKg = 60.0), SetValues(reps = 6, weightKg = 65.0)), exercise.sets.map { it.values })

        val summary = workouts.history().first().single()
        assertEquals(2, summary.setCount)
        assertEquals(60.0 * 8 + 65.0 * 6, summary.volumeKg)
        assertEquals(listOf("Bench Press"), summary.exercises.map { it.name })
        assertEquals(listOf(2), summary.exercises.map { it.setCount })
    }

    @Test
    fun theNextWorkoutStartsFromLastTime() = runTest {
        logBench(40.0 to 10, 60.0 to 8, 60.0 to 8, warmups = 1)

        val id = workouts.startWorkout("Push again")
        workouts.addExercises(id, listOf("ex-bench"))
        val exercise = active().exercises.single()
        assertEquals(3, exercise.sets.size)
        assertEquals(listOf(true, false, false), exercise.sets.map { it.isWarmup })
        assertEquals(listOf(40.0, 60.0, 60.0), exercise.previous.map { it.values.weightKg })
        assertEquals(listOf(10, 8, 8), exercise.previous.map { it.values.reps })
    }

    @Test
    fun warmupsDontCountTowardsVolume() = runTest {
        logBench(40.0 to 10, 60.0 to 8, warmups = 1)
        assertEquals(480.0, workouts.history().first().single().volumeKg)
    }

    @Test
    fun removingSetsAndExercisesKeepsPositionsTidy() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench", "ex-plank", "ex-curl"))
        val bench = active().exercises.first()
        workouts.addSet(bench.id)
        workouts.addSet(bench.id)
        val sets = active().exercises.first().sets
        workouts.updateSet(sets[2].id, SetValues(reps = 3))
        workouts.removeSet(sets[1].id)
        assertEquals(listOf(null, 3), active().exercises.first().sets.map { it.values.reps })

        workouts.removeExercise(active().exercises[1].id)
        assertEquals(listOf("Bench Press", "Barbell Curl"), active().exercises.map { it.name })

        workouts.moveExercise(active().exercises[1].id, offset = -1)
        assertEquals(listOf("Barbell Curl", "Bench Press"), active().exercises.map { it.name })
        workouts.moveExercise(active().exercises[0].id, offset = -1)
        assertEquals(listOf("Barbell Curl", "Bench Press"), active().exercises.map { it.name })
    }

    @Test
    fun deletingAWorkoutRemovesItEverywhere() = runTest {
        val id = logBench(60.0 to 8)
        assertEquals(1, workouts.history().first().size)
        workouts.deleteWorkout(id)
        assertTrue(workouts.history().first().isEmpty())
        assertNull(workouts.workout(id).first())
        assertTrue(workouts.exerciseHistory("ex-bench").first().isEmpty())

        val next = workouts.startWorkout("Push")
        workouts.addExercises(next, listOf("ex-bench"))
        assertTrue(active().exercises.single().previous.isEmpty())
    }

    @Test
    fun exerciseHistoryIsNewestFirst() = runTest {
        logBench(60.0 to 8)
        logBench(62.5 to 8, 62.5 to 7)
        val sessions = workouts.exerciseHistory("ex-bench").first()
        assertEquals(2, sessions.size)
        assertEquals(listOf(62.5, 62.5), sessions[0].sets.map { it.values.weightKg })
        assertEquals(listOf(60.0), sessions[1].sets.map { it.values.weightKg })
        assertEquals(listOf("ex-bench"), workouts.recentExerciseIds().first())
    }

    @Test
    fun renamingIgnoresBlankNames() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.renameWorkout(id, "  Chest day ")
        assertEquals("Chest day", active().name)
        workouts.renameWorkout(id, "   ")
        assertEquals("Chest day", active().name)
    }

    @Test
    fun aDeletedSetCanBePutBack() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench"))
        val bench = active().exercises.single()
        workouts.addSet(bench.id)
        workouts.addSet(bench.id)
        val sets = active().exercises.single().sets
        sets.forEachIndexed { index, set -> workouts.updateSet(set.id, SetValues(reps = index + 1)) }

        workouts.removeSet(sets[1].id)
        assertEquals(listOf(1, 3), active().exercises.single().sets.map { it.values.reps })
        workouts.restoreSet(sets[1].id)
        assertEquals(listOf(1, 2, 3), active().exercises.single().sets.map { it.values.reps })
        // Restoring twice changes nothing.
        workouts.restoreSet(sets[1].id)
        assertEquals(listOf(1, 2, 3), active().exercises.single().sets.map { it.values.reps })

        // Back at the end when the sets after it are gone too.
        workouts.removeSet(sets[1].id)
        workouts.removeSet(sets[2].id)
        workouts.restoreSet(sets[1].id)
        assertEquals(listOf(1, 2), active().exercises.single().sets.map { it.values.reps })
    }

    @Test
    fun exercisesCanBeReordered() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench", "ex-plank", "ex-curl"))
        val ids = active().exercises.map { it.id }
        workouts.reorderExercises(id, listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("Barbell Curl", "Bench Press", "Plank"), active().exercises.map { it.name })
        // Unknown IDs are ignored and missing ones keep their order at the end.
        workouts.reorderExercises(id, listOf("nope", ids[1]))
        assertEquals(listOf("Plank", "Barbell Curl", "Bench Press"), active().exercises.map { it.name })
    }

    @Test
    fun restTimesAreRememberedPerExercise() = runTest {
        val id = workouts.startWorkout("Push")
        workouts.addExercises(id, listOf("ex-bench", "ex-plank"))
        assertEquals(listOf(null, null), active().exercises.map { it.restSeconds })
        assertEquals(listOf(90, 90), active().exercises.map { it.rest(defaultSeconds = 90) })

        val bench = active().exercises.first()
        workouts.setExerciseRest(bench.id, 150)
        workouts.setExerciseRest(active().exercises[1].id, 0)
        assertEquals(listOf(150, 0), active().exercises.map { it.restSeconds })
        workouts.completeSet(bench.sets.single().id, SetValues(weightKg = 60.0, reps = 5))
        assertTrue(workouts.finishWorkout(id))

        clock.advance(24 * 60 * 60 * 1000)
        val next = workouts.startWorkout("Push again")
        workouts.addExercises(next, listOf("ex-plank", "ex-bench", "ex-curl"))
        // The plank was dropped from the finished workout (no sets ticked), so its "off" is forgotten.
        assertEquals(listOf(null, 150, null), active().exercises.map { it.restSeconds })

        // Too long is capped, and null goes back to the default.
        val curl = active().exercises.last()
        workouts.setExerciseRest(curl.id, 10_000)
        assertEquals(600, active().exercises.last().restSeconds)
        workouts.setExerciseRest(curl.id, null)
        assertNull(active().exercises.last().restSeconds)
    }
}
