package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseSearchTest {
    private fun exercise(name: String, equipment: String, vararg muscles: Muscle) = ExerciseSummary(
        id = name,
        name = name,
        measurementType = MeasurementType.WeightReps,
        equipmentId = equipment,
        equipmentName = equipment,
        primaryMuscles = muscles.toList(),
        secondaryMuscles = emptyList(),
    )

    private val library = listOf(
        exercise("Barbell Bench Press - Medium Grip", "Barbell", Muscle.Chest),
        exercise("Bench Press - Powerlifting", "Barbell", Muscle.Triceps),
        exercise("Dumbbell Bench Press", "Dumbbell", Muscle.Chest),
        exercise("Leg Press", "Machine", Muscle.Quadriceps),
        exercise("Pullups", "Bodyweight", Muscle.Lats),
    )

    private fun names(query: String, muscle: Muscle? = null, equipment: String? = null) =
        ExerciseSearch.filter(library, query, muscle, equipment).map { it.name }

    @Test
    fun emptyQueryKeepsEverythingInOrder() {
        assertEquals(library.map { it.name }, names(""))
    }

    @Test
    fun wordsCanComeInAnyOrder() {
        assertEquals(
            listOf("Bench Press - Powerlifting", "Barbell Bench Press - Medium Grip", "Dumbbell Bench Press"),
            names("bench press"),
        )
        assertEquals(names("bench press").toSet(), names("press bench").toSet())
    }

    @Test
    fun matchesEquipmentAndMuscles() {
        assertEquals(listOf("Leg Press"), names("quads"))
        assertEquals(listOf("Dumbbell Bench Press"), names("dumbbell chest"))
    }

    @Test
    fun filtersByMuscleAndEquipment() {
        assertEquals(listOf("Barbell Bench Press - Medium Grip", "Dumbbell Bench Press"), names("", muscle = Muscle.Chest))
        assertEquals(listOf("Barbell Bench Press - Medium Grip", "Bench Press - Powerlifting"), names("press", equipment = "Barbell"))
    }
}
