package com.tomhartill.kinzo.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineTest {
    private val push = Routine(
        id = "kz-push",
        name = "Push",
        notes = "Chest day.",
        source = RoutineSource.Kinzo,
        splitId = "split-push",
        splitName = "Push",
        exercises = listOf(
            RoutineExercise("a", "ex-bench", "Bench Press", MeasurementType.WeightReps, 4, SetValues(reps = 8, weightKg = 61.235), 150),
            RoutineExercise("b", "ex-plank", "Plank", MeasurementType.Duration, 3, SetValues(durationS = 45)),
        ),
    )

    @Test
    fun aCopyIsTheUsersOwnWithNewIds() {
        val copy = push.copyAsNew(name = "My push", mesocycleId = "meso-2")
        assertEquals(RoutineSource.User, copy.source)
        assertEquals("My push", copy.name)
        assertEquals(push.notes, copy.notes)
        assertEquals(WorkoutType.Resistance, copy.type)
        assertEquals("split-push", copy.splitId)
        assertEquals("Push", copy.splitName)
        assertEquals("meso-2", copy.mesocycleId)
        assertEquals(push.exercises.map { it.copy(id = "") }, copy.exercises.map { it.copy(id = "") })
        val ids = listOf(push.id) + push.exercises.map { it.id }
        assertTrue((listOf(copy.id) + copy.exercises.map { it.id }).none { it in ids })
        assertEquals(7, push.totalSets)
    }

    @Test
    fun newWorkoutsAreNamedAfterTheirSplitAndTheTimeOfDay() {
        assertEquals("Morning push", WorkoutType.Resistance.suggestedName("Push", "Morning"))
        assertEquals("Evening full body", WorkoutType.Resistance.suggestedName(" Full body ", "Evening"))
        assertEquals("Afternoon PPL A", WorkoutType.Resistance.suggestedName("PPL A", "Afternoon"))
        assertEquals("Morning workout", WorkoutType.Resistance.suggestedName(null, "Morning"))
        assertEquals("Night workout", WorkoutType.Resistance.suggestedName("  ", "Night"))
        assertEquals("Morning swim", WorkoutType.Swimming.suggestedName(null, "Morning"))
        assertEquals("Evening HIIT", WorkoutType.Hiit.suggestedName(null, "Evening"))
        assertEquals(WorkoutType.Resistance, WorkoutType.fromKey(null))
        assertEquals(WorkoutType.Swimming, WorkoutType.fromKey("swimming"))
        assertEquals(WorkoutType.entries.size, WorkoutType.entries.map { it.key }.toSet().size)
    }

    @Test
    fun mesocyclesCountWeeksFromTheirStart() {
        val block = Mesocycle("m", "Mesocycle 1", start = LocalDate(2026, 10, 14), end = LocalDate(2027, 1, 5))
        assertEquals(12, block.weeks)
        assertEquals(13, block.copy(end = LocalDate(2027, 1, 6)).weeks)
        assertEquals(1, block.copy(end = block.start).weeks)
        assertNull(block.weekOf(LocalDate(2026, 10, 13)))
        assertEquals(1, block.weekOf(LocalDate(2026, 10, 14)))
        assertEquals(1, block.weekOf(LocalDate(2026, 10, 20)))
        assertEquals(2, block.weekOf(LocalDate(2026, 10, 21)))
        assertEquals(12, block.weekOf(LocalDate(2027, 1, 5)))
        assertEquals(13, block.weekOf(LocalDate(2027, 1, 6)))
    }

    // The editor keeps an unsaved workout as JSON while the app is in the background.
    @Test
    fun aDraftSurvivesJson() {
        val json = Json.encodeToString(Routine.serializer(), push)
        assertEquals(push, Json.decodeFromString(Routine.serializer(), json))
    }
}
