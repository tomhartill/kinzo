package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExerciseGroupsTest {
    private fun exercise(id: String, name: String, groupId: String? = null, groupName: String? = null) = ExerciseSummary(
        id = id,
        name = name,
        measurementType = MeasurementType.WeightReps,
        equipmentId = null,
        equipmentName = null,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        groupId = groupId,
        groupName = groupName,
    )

    @Test
    fun variationsFoldIntoOneRowPlacedByTheGroupName() {
        val rows = ExerciseGroups.rows(
            listOf(
                exercise("a", "Arnold Press"),
                exercise("i", "Machine Incline Chest Press", "press", "Chest press"),
                exercise("d", "Machine Decline Chest Press", "press", "Chest press"),
                exercise("p", "Plank"),
            ),
        )

        assertEquals(listOf("Arnold Press", "Chest press", "Plank"), rows.map { it.name })
        val group = assertIs<ExerciseListRow.Group>(rows[1])
        assertEquals(listOf("Machine Decline Chest Press", "Machine Incline Chest Press"), group.exercises.map { it.name })
    }

    @Test
    fun aGroupWithOneExerciseLeftShowsItOnItsOwn() {
        val rows = ExerciseGroups.rows(listOf(exercise("i", "Machine Incline Chest Press", "press", "Chest press")))

        assertEquals(listOf("Machine Incline Chest Press"), rows.map { it.name })
        assertIs<ExerciseListRow.Single>(rows.single())
    }
}
