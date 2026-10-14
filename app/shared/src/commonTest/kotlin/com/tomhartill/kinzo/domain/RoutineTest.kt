package com.tomhartill.kinzo.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineTest {
    private val push = Routine(
        id = "kz-push",
        name = "Push",
        notes = "Chest day.",
        source = RoutineSource.Kinzo,
        exercises = listOf(
            RoutineExercise("a", "ex-bench", "Bench Press", MeasurementType.WeightReps, 4, SetValues(reps = 8, weightKg = 61.235), 150),
            RoutineExercise("b", "ex-plank", "Plank", MeasurementType.Duration, 3, SetValues(durationS = 45)),
        ),
    )

    @Test
    fun aCopyIsTheUsersOwnWithNewIds() {
        val copy = push.copyAsNew(name = "My push")
        assertEquals(RoutineSource.User, copy.source)
        assertEquals("My push", copy.name)
        assertEquals(push.notes, copy.notes)
        assertEquals(push.exercises.map { it.copy(id = "") }, copy.exercises.map { it.copy(id = "") })
        val ids = listOf(push.id) + push.exercises.map { it.id }
        assertTrue((listOf(copy.id) + copy.exercises.map { it.id }).none { it in ids })
        assertEquals(7, push.totalSets)
    }

    // The editor keeps an unsaved workout as JSON while the app is in the background.
    @Test
    fun aDraftSurvivesJson() {
        val json = Json.encodeToString(Routine.serializer(), push)
        assertEquals(push, Json.decodeFromString(Routine.serializer(), json))
    }
}
