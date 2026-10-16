package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineExercise
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.SetValues
import com.tomhartill.kinzo.domain.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MesocycleRepositoryTest {
    private val db = inMemoryDatabase()

    // 2025-10-09 in UTC.
    private val clock = FakeClock()
    private val mesocycles = MesocycleRepository(db, clock, Dispatchers.Unconfined) { TimeZone.UTC }
    private val routines = RoutineRepository(db, clock, Dispatchers.Unconfined) { TimeZone.UTC }
    private val workouts = WorkoutRepository(db, clock, Dispatchers.Unconfined)
    private val day = 86_400_000L

    private val bench = RoutineExercise(
        id = "re-bench",
        exerciseId = "ex-bench",
        name = "Bench Press",
        measurementType = MeasurementType.WeightReps,
        targetSets = 3,
        target = SetValues(reps = 5, weightKg = 60.0),
        restSeconds = 120,
    )

    @BeforeTest
    fun seed() = runTest {
        CatalogueImporter(db, clock, Dispatchers.Unconfined) { smallCatalogue() }.importIfNeeded()
        KinzoWorkoutsImporter(db, clock, Dispatchers.Unconfined) {
            """{"version":1,"workouts":[{"id":"kz-push","name":"Push","exercises":[{"id":"kz-push-0","exerciseId":"ex-bench","sets":3,"reps":8}]}]}"""
        }.importIfNeeded()
    }

    @Test
    fun theFirstMesocycleStartsTodayAndTakesInSavedWorkouts() = runTest {
        // Saved before mesocycles existed.
        routines.save(Routine("mine", "Chest", null, RoutineSource.User, listOf(bench)))
        assertNull(assertNotNull(routines.routine("mine").first()).mesocycleId)
        assertNull(mesocycles.current().first())

        val first = mesocycles.ensureCurrent()
        assertEquals("Mesocycle 1", first.name)
        assertEquals(LocalDate(2025, 10, 9), first.start)
        assertEquals(LocalDate(2025, 12, 31), first.end)
        assertEquals(12, first.weeks)
        assertEquals(first, mesocycles.current().first())
        assertEquals(first.id, assertNotNull(routines.routine("mine").first()).mesocycleId)
        // Ready-made workouts don't belong to one.
        assertNull(assertNotNull(routines.routine("kz-push").first()).mesocycleId)

        // Running again changes nothing.
        clock.advance(day * 30)
        assertEquals(first, mesocycles.ensureCurrent())
        assertEquals(listOf(first), mesocycles.mesocycles().first())
        assertEquals("Mesocycle 2", mesocycles.nextName())
    }

    @Test
    fun theFirstMesocycleStartsWhenTheUserFirstTrained() = runTest {
        clock.advance(-day * 20)
        val workout = workouts.startWorkout("Legs")
        workouts.finishWorkout(workout)
        clock.advance(day * 20)
        assertEquals(LocalDate(2025, 9, 19), mesocycles.ensureCurrent().start)
        assertEquals("Week 3", "Week ${mesocycles.current().first()?.weekOf(mesocycles.today())}")
    }

    @Test
    fun newWorkoutsGoIntoTheCurrentMesocycle() = runTest {
        val first = mesocycles.ensureCurrent()
        routines.save(Routine("mine", "Legs", null, RoutineSource.User, emptyList(), type = WorkoutType.Running))
        val saved = assertNotNull(routines.routine("mine").first())
        assertEquals(first.id, saved.mesocycleId)
        assertEquals(WorkoutType.Running, saved.type)
    }

    @Test
    fun startingTheNextOneCopiesTheWorkoutsToChange() = runTest {
        val first = mesocycles.ensureCurrent()
        routines.save(Routine("older", "Back", null, RoutineSource.User, listOf(bench.copy(id = "re-older"))))
        clock.advance(1_000)
        routines.save(Routine("newer", "Chest", "Heavy.", RoutineSource.User, listOf(bench), splitId = "split-push"))

        val start = LocalDate(2025, 11, 1)
        val second = assertNotNull(mesocycles.startNext(" ", start, LocalDate(2026, 1, 31), copyWorkouts = true))
        assertEquals("Mesocycle 2", second.name)
        val trimmed = first.copy(end = LocalDate(2025, 10, 31))
        assertEquals(listOf(second, trimmed), mesocycles.mesocycles().first())
        // The first carries on until the second starts.
        assertEquals(trimmed, mesocycles.current().first())
        routines.save(Routine("later", "Legs", null, RoutineSource.User, emptyList()))
        assertEquals(first.id, assertNotNull(routines.routine("later").first()).mesocycleId)
        clock.advance(day * 23)
        assertEquals(second, mesocycles.current().first())
        assertEquals(second, mesocycles.ensureCurrent())

        val copies = routines.routines().first().filter { it.mesocycleId == second.id }
        assertEquals(listOf("Chest", "Back"), copies.map { it.name }.filter { it != "Legs" })
        val copy = assertNotNull(routines.routine(copies.first().id).first())
        assertEquals("Heavy.", copy.notes)
        assertEquals("split-push", copy.splitId)
        assertEquals(listOf(bench.copy(id = copy.exercises.single().id)), copy.exercises)
        assertFalse(copy.exercises.single().id == "re-bench")
        // The originals stay with the first mesocycle.
        assertEquals(first.id, assertNotNull(routines.routine("newer").first()).mesocycleId)
        assertEquals(listOf("re-bench"), assertNotNull(routines.routine("newer").first()).exercises.map { it.id })
    }

    @Test
    fun startingEmptyLeavesTheOldWorkoutsBehind() = runTest {
        mesocycles.ensureCurrent()
        routines.save(Routine("mine", "Chest", null, RoutineSource.User, listOf(bench)))
        // It starts after the first one ends, which keeps its dates.
        val second = assertNotNull(mesocycles.startNext("Strength", LocalDate(2026, 2, 1), LocalDate(2026, 3, 31), copyWorkouts = false))
        assertEquals("Strength", second.name)
        assertEquals(LocalDate(2025, 12, 31), mesocycles.mesocycles().first()[1].end)
        assertTrue(routines.routines().first().none { it.mesocycleId == second.id })
    }

    @Test
    fun datesMustMakeSense() = runTest {
        val first = mesocycles.ensureCurrent()
        assertNull(mesocycles.startNext("Early", first.start, LocalDate(2026, 3, 1), copyWorkouts = false))
        assertNull(mesocycles.startNext("Backwards", LocalDate(2025, 12, 1), LocalDate(2025, 11, 1), copyWorkouts = false))
        assertEquals(1, mesocycles.mesocycles().first().size)

        assertFalse(mesocycles.update(first.id, "Base", LocalDate(2025, 10, 9), LocalDate(2025, 10, 8)))
        assertFalse(mesocycles.update("nope", "Base", LocalDate(2025, 10, 9), LocalDate(2025, 12, 8)))
        assertTrue(mesocycles.update(first.id, "  ", LocalDate(2025, 10, 6), LocalDate(2025, 12, 28)))
        assertEquals(first.copy(start = LocalDate(2025, 10, 6), end = LocalDate(2025, 12, 28)), mesocycles.current().first())
        assertTrue(mesocycles.update(first.id, "Base", LocalDate(2025, 10, 6), LocalDate(2025, 12, 28)))
        assertEquals("Base", mesocycles.current().first()?.name)

        // Moving a start past a neighbour's would reorder them, so it's refused.
        val second = assertNotNull(mesocycles.startNext("Build", LocalDate(2025, 11, 1), LocalDate(2025, 12, 31), copyWorkouts = false))
        assertFalse(mesocycles.update(first.id, "Base", LocalDate(2025, 11, 1), LocalDate(2025, 12, 31)))
        assertFalse(mesocycles.update(second.id, "Build", LocalDate(2025, 10, 6), LocalDate(2025, 12, 31)))
        assertTrue(mesocycles.update(second.id, "Build", LocalDate(2025, 10, 7), LocalDate(2025, 12, 31)))
        // It has started now, so it's the current one.
        assertEquals("Build", mesocycles.current().first()?.name)
    }
}
