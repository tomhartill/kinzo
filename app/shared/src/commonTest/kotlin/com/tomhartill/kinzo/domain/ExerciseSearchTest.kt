package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseSearchTest {
    private fun exercise(
        name: String,
        equipment: String,
        vararg muscles: Muscle,
        aliases: List<String> = emptyList(),
        group: String? = null,
        groupAliases: List<String> = emptyList(),
    ) = ExerciseSummary(
        id = name,
        name = name,
        measurementType = MeasurementType.WeightReps,
        equipmentId = equipment,
        equipmentName = equipment,
        primaryMuscles = muscles.toList(),
        secondaryMuscles = emptyList(),
        groupId = group,
        groupName = group,
        aliases = aliases,
        groupAliases = groupAliases,
    )

    private val overheadPress = listOf("shoulder press", "military press", "strict press", "OHP")

    private val library = listOf(
        exercise("Arnold Press", "Dumbbell", Muscle.Shoulders, group = "Overhead press", groupAliases = overheadPress),
        exercise(
            "Bench Press", "Barbell", Muscle.Chest,
            aliases = listOf("Barbell Bench Press", "Flat Bench Press", "Medium-Grip Bench Press"),
            group = "Bench press",
        ),
        exercise("Close-Grip Bench Press", "Barbell", Muscle.Triceps, group = "Bench press"),
        exercise("Dumbbell Bench Press", "Dumbbell", Muscle.Chest, group = "Bench press"),
        exercise("Dumbbell Shoulder Press", "Dumbbell", Muscle.Shoulders, group = "Overhead press", groupAliases = overheadPress),
        exercise("Leg Press", "Machine", Muscle.Quadriceps),
        exercise(
            "Overhead Press", "Barbell", Muscle.Shoulders,
            aliases = listOf("Military Press", "Strict Press", "OHP", "Shoulder Press"),
            group = "Overhead press", groupAliases = overheadPress,
        ),
        exercise("Pull-Up", "Bodyweight", Muscle.Lats, aliases = listOf("Pullups")),
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
            listOf("Bench Press", "Close-Grip Bench Press", "Dumbbell Bench Press"),
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
        assertEquals(listOf("Bench Press", "Dumbbell Bench Press"), names("", muscle = Muscle.Chest))
        assertEquals(listOf("Bench Press", "Close-Grip Bench Press", "Overhead Press"), names("press", equipment = "Barbell"))
    }

    @Test
    fun anAliasFindsTheExerciseAndRanksItFirst() {
        assertEquals("Overhead Press", names("military press").first())
        assertEquals("Overhead Press", names("strict press").first())
        assertEquals("Overhead Press", names("ohp").first())
        assertEquals("Overhead Press", names("shoulder press").first())
        // The old free-exercise-db name, words in any order, still finds the plain bench press.
        assertEquals(listOf("Bench Press"), names("medium grip barbell bench press"))
    }

    @Test
    fun searchingAGroupListsEveryVariationAfterTheBestMatch() {
        assertEquals(
            listOf("Overhead Press", "Arnold Press", "Dumbbell Shoulder Press"),
            names("overhead press"),
        )
        assertEquals(
            listOf("Overhead Press", "Arnold Press", "Dumbbell Shoulder Press"),
            names("military press"),
        )
    }

    @Test
    fun punctuationIsOptional() {
        assertEquals(listOf("Pull-Up"), names("pullup"))
        assertEquals(listOf("Pull-Up"), names("pull up"))
        assertEquals(listOf("Pull-Up"), names("Pull-up"))
    }
}
