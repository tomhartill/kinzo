package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.data.WorkoutRepository.StartResult
import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.SetValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val routines = RoutineRepository(db, clock, Dispatchers.Unconfined)
    private val workouts = WorkoutRepository(db, clock, Dispatchers.Unconfined)

    @BeforeTest
    fun seed() = runTest {
        CatalogueImporter(db, clock, Dispatchers.Unconfined) { smallCatalogue() }.importIfNeeded()
        KinzoWorkoutsImporter(db, clock, Dispatchers.Unconfined) { bundledWorkouts() }.importIfNeeded()
    }

    private fun bundledWorkouts(version: Int = 1, includeCore: Boolean = true): String {
        val core = if (includeCore) {
            """,{"id":"kz-core","name":"Core","notes":"Abs.","exercises":[
                {"id":"kz-core-0","exerciseId":"ex-plank","sets":3,"durationS":45,"restS":30}]}"""
        } else {
            ""
        }
        return """{"version":$version,"workouts":[
            {"id":"kz-push","name":"Push","notes":"Chest day.","exercises":[
              {"id":"kz-push-0","exerciseId":"ex-bench","sets":4,"reps":8,"restS":150},
              {"id":"kz-push-1","exerciseId":"ex-plank","sets":2,"durationS":30,"restS":null}]}
            $core]}"""
    }

    private fun bench(sets: Int = 3, reps: Int? = 5, weightKg: Double? = null, rest: Int? = null, id: String = "re-bench") =
        RoutineExercise(
            id = id,
            exerciseId = "ex-bench",
            name = "Bench Press",
            measurementType = MeasurementType.WeightReps,
            targetSets = sets,
            target = SetValues(reps = reps, weightKg = weightKg),
            restSeconds = rest,
        )

    private val curl = RoutineExercise(
        id = "re-curl",
        exerciseId = "ex-curl",
        name = "Barbell Curl",
        measurementType = MeasurementType.WeightReps,
        targetSets = 2,
        target = SetValues(reps = 12),
    )

    @Test
    fun readyMadeWorkoutsAreImportedInOrder() = runTest {
        val list = routines.routines().first()
        assertEquals(listOf("Push", "Core"), list.map { it.name })
        assertTrue(list.all { it.source == RoutineSource.Kinzo })
        assertEquals(listOf("Bench Press", "Plank"), list.first().exerciseNames)

        val push = assertNotNull(routines.routine("kz-push").first())
        assertFalse(push.isEditable)
        assertEquals(listOf(4, 2), push.exercises.map { it.targetSets })
        assertEquals(SetValues(reps = 8), push.exercises[0].target)
        assertEquals(SetValues(durationS = 30), push.exercises[1].target)
        assertEquals(listOf(150, null), push.exercises.map { it.restSeconds })
    }

    @Test
    fun aNewBundleReplacesTheOldOne() = runTest {
        assertFalse(KinzoWorkoutsImporter(db, clock, Dispatchers.Unconfined) { bundledWorkouts() }.importIfNeeded())
        assertTrue(KinzoWorkoutsImporter(db, clock, Dispatchers.Unconfined) { bundledWorkouts(version = 2, includeCore = false) }.importIfNeeded())
        assertEquals(listOf("Push"), routines.routines().first().map { it.name })
        assertNull(routines.routine("kz-core").first())
        assertEquals(2, assertNotNull(routines.routine("kz-push").first()).exercises.size)
    }

    @Test
    fun savingCreatesThenEditsTheUsersOwnWorkout() = runTest {
        val draft = Routine(id = "mine", name = "  Chest  ", notes = " ", source = RoutineSource.User, exercises = listOf(bench(), curl))
        assertTrue(routines.save(draft))
        val list = routines.routines().first()
        assertEquals(listOf("Chest", "Push", "Core"), list.map { it.name })
        val saved = assertNotNull(routines.routine("mine").first())
        assertTrue(saved.isEditable)
        assertNull(saved.notes)
        assertEquals(listOf("Bench Press", "Barbell Curl"), saved.exercises.map { it.name })

        // Reorder, drop one, change targets, add one back with a new ID.
        clock.advance(1_000)
        val edited = saved.copy(
            name = "",
            exercises = listOf(curl.copy(targetSets = 99), bench(sets = 0, reps = 0, weightKg = 61.23456, rest = 9_999, id = "re-bench-2")),
        )
        assertTrue(routines.save(edited))
        val again = assertNotNull(routines.routine("mine").first())
        assertEquals(RoutineRepository.UNTITLED, again.name)
        assertEquals(listOf("re-curl", "re-bench-2"), again.exercises.map { it.id })
        assertEquals(RoutineExercise.MAX_SETS, again.exercises[0].targetSets)
        assertEquals(RoutineExercise.MIN_SETS, again.exercises[1].targetSets)
        assertEquals(SetValues(weightKg = 61.235), again.exercises[1].target)
        assertEquals(600, again.exercises[1].restSeconds)
        assertEquals(listOf("Barbell Curl", "Bench Press"), routines.routines().first().first().exerciseNames)
    }

    @Test
    fun readyMadeWorkoutsAreCopiedNotChanged() = runTest {
        val push = assertNotNull(routines.routine("kz-push").first())
        assertFalse(routines.save(push.copy(name = "Mine now")))
        assertEquals("Push", assertNotNull(routines.routine("kz-push").first()).name)

        routines.delete("kz-push")
        assertNotNull(routines.routine("kz-push").first())

        val draft = push.copyAsNew()
        assertTrue(routines.save(draft))
        val copyId = draft.id
        val copy = assertNotNull(routines.routine(copyId).first())
        assertEquals(RoutineSource.User, copy.source)
        assertEquals("Push", copy.name)
        assertEquals(push.notes, copy.notes)
        assertEquals(push.exercises.map { it.exerciseId }, copy.exercises.map { it.exerciseId })
        assertEquals(push.exercises.map { it.target }, copy.exercises.map { it.target })
        assertEquals(push.exercises.map { it.restSeconds }, copy.exercises.map { it.restSeconds })
        assertTrue(copy.exercises.none { it.id in push.exercises.map { original -> original.id } })
        assertEquals(copyId, routines.routines().first().first().id)
        // The original is untouched.
        assertEquals(push, routines.routine("kz-push").first())

        routines.delete(copyId)
        assertNull(routines.routine(copyId).first())
        assertEquals(listOf("Push", "Core"), routines.routines().first().map { it.name })
    }

    @Test
    fun startingFromASavedWorkoutFillsInThePlan() = runTest {
        routines.save(Routine("mine", "Chest", null, RoutineSource.User, listOf(bench(sets = 2, reps = 5, weightKg = 60.0, rest = 120), curl)))
        assertEquals(StartResult.NotFound, workouts.startFromRoutine("nope"))

        // A rest picked for curls in an earlier workout doesn't override the plan, which shows
        // the default for them.
        val earlier = workouts.startWorkout("Arms")
        workouts.addExercises(earlier, listOf("ex-curl"))
        val earlierCurl = assertNotNull(workouts.activeWorkout().first()).exercises.single()
        workouts.setExerciseRest(earlierCurl.id, 45)
        workouts.completeSet(earlierCurl.sets.first().id, SetValues(reps = 10, weightKg = 30.0))
        assertTrue(workouts.finishWorkout(earlier))
        clock.advance(60_000)

        val result = assertIs<StartResult.Started>(workouts.startFromRoutine("mine"))
        val workout = assertNotNull(workouts.activeWorkout().first())
        assertEquals(result.workoutId, workout.id)
        assertEquals("Chest", workout.name)
        assertEquals(listOf("Bench Press", "Barbell Curl"), workout.exercises.map { it.name })
        assertEquals(listOf(SetValues(reps = 5, weightKg = 60.0), SetValues(reps = 5, weightKg = 60.0)), workout.exercises[0].sets.map { it.values })
        assertEquals(List(2) { SetValues(reps = 12) }, workout.exercises[1].sets.map { it.values })
        assertTrue(workout.exercises.flatMap { it.sets }.none { it.isCompleted })
        assertEquals(listOf(120, null), workout.exercises.map { it.restSeconds })

        assertEquals(StartResult.AlreadyInProgress(workout.id), workouts.startFromRoutine("kz-push"))

        // Using it moves it to the top of the user's list, and it's marked as used.
        workouts.completeSet(workout.exercises[0].sets[0].id, SetValues(reps = 5, weightKg = 60.0))
        assertTrue(workouts.finishWorkout(workout.id))
        assertNotNull(routines.routines().first().single { it.id == "mine" }.lastUsedAt)
    }

    @Test
    fun readyMadeTimedExercisesStartWithTheirTime() = runTest {
        assertIs<StartResult.Started>(workouts.startFromRoutine("kz-push"))
        val workout = assertNotNull(workouts.activeWorkout().first())
        assertEquals("Push", workout.name)
        assertEquals(4, workout.exercises[0].sets.size)
        assertEquals(SetValues(reps = 8), workout.exercises[0].sets[0].values)
        assertEquals(SetValues(durationS = 30), workout.exercises[1].sets[0].values)
        assertEquals(listOf(150, null), workout.exercises.map { it.restSeconds })
    }

    @Test
    fun theBundledWorkoutsMatchTheBundledCatalogue() = runTest {
        val fresh = inMemoryDatabase()
        val catalogue = File("src/commonMain/composeResources/files/catalogue.json").readText()
        val bundled = File("src/commonMain/composeResources/files/workouts.json").readText()
        CatalogueImporter(fresh, clock, Dispatchers.Unconfined) { catalogue }.importIfNeeded()
        assertTrue(KinzoWorkoutsImporter(fresh, clock, Dispatchers.Unconfined) { bundled }.importIfNeeded())

        val freshRoutines = RoutineRepository(fresh, clock, Dispatchers.Unconfined)
        val list = freshRoutines.routines().first()
        assertTrue(list.size >= 8)
        list.forEach { summary ->
            val routine = assertNotNull(freshRoutines.routine(summary.id).first())
            // Every exercise was found in the catalogue (the join would drop it otherwise).
            assertEquals(summary.exerciseNames.size, routine.exercises.size)
            assertTrue(routine.exercises.isNotEmpty(), summary.name)
            routine.exercises.forEach { exercise ->
                val hasTarget = when (exercise.measurementType) {
                    MeasurementType.WeightReps, MeasurementType.Reps -> exercise.target.reps != null
                    MeasurementType.Duration, MeasurementType.DistanceDuration -> exercise.target.durationS != null
                }
                assertTrue(hasTarget, "${summary.name}: ${exercise.name}")
            }
        }
    }
}
