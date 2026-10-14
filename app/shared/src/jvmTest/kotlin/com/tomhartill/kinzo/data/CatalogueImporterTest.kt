package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.MeasurementType
import com.tomhartill.kinzo.domain.Muscle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogueImporterTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val catalogue = CatalogueRepository(db, Dispatchers.Unconfined)

    private fun importer(json: String) = CatalogueImporter(db, clock, Dispatchers.Unconfined) { json }

    @Test
    fun importsOnceUntilTheVersionChanges() = runTest {
        assertTrue(importer(smallCatalogue(version = 1)).importIfNeeded())
        assertFalse(importer(smallCatalogue(version = 1)).importIfNeeded())

        val exercises = catalogue.exercises().first()
        assertEquals(listOf("Barbell Curl", "Bench Press", "Plank"), exercises.map { it.name })
        val bench = exercises.single { it.name == "Bench Press" }
        assertEquals(MeasurementType.WeightReps, bench.measurementType)
        assertEquals("Barbell", bench.equipmentName)
        assertEquals(listOf(Muscle.Chest), bench.primaryMuscles)
        assertEquals(setOf(Muscle.Shoulders, Muscle.Triceps), bench.secondaryMuscles.toSet())
        assertEquals("bench_press", bench.icon)
        assertEquals(null, exercises.single { it.name == "Plank" }.icon)
    }

    @Test
    fun aNewVersionDropsRemovedExercisesButKeepsThemReadable() = runTest {
        importer(smallCatalogue(version = 1)).importIfNeeded()
        assertTrue(importer(smallCatalogue(version = 2, includeCurl = false)).importIfNeeded())

        assertEquals(listOf("Bench Press", "Plank"), catalogue.exercises().first().map { it.name })
        // Old workouts can still show the name of an exercise that left the catalogue.
        assertEquals("Barbell Curl", catalogue.exercise("ex-curl").first()?.name)
    }

    @Test
    fun exerciseDetailSplitsInstructionsIntoSteps() = runTest {
        importer(smallCatalogue()).importIfNeeded()
        val bench = assertNotNull(catalogue.exercise("ex-bench").first())
        assertEquals(listOf("Lie on the bench.", "Press the bar."), bench.instructions)
        assertEquals("bench_press", bench.icon)
        assertEquals(listOf(Muscle.Chest), bench.primaryMuscles)
    }

    @Test
    fun theBundledCatalogueIsValid() = runTest {
        val file = File("src/commonMain/composeResources/files/catalogue.json")
        val parsed = json.decodeFromString(CatalogueFile.serializer(), file.readText())
        assertTrue(parsed.exercises.size > 800)
        assertEquals(parsed.exercises.size, parsed.exercises.map { it.id }.toSet().size)
        val equipmentIds = parsed.equipment.map { it.id }.toSet()
        parsed.exercises.forEach { exercise ->
            assertTrue(MeasurementType.entries.any { it.key == exercise.measurementType }, exercise.name)
            assertTrue(exercise.equipmentId == null || exercise.equipmentId in equipmentIds, exercise.name)
            (exercise.primaryMuscles + exercise.secondaryMuscles).forEach { assertNotNull(Muscle.fromKey(it), it) }
        }

        // Every exercise has a pictogram, and every one they name is drawn.
        val icons = json.parseToJsonElement(File("src/commonMain/composeResources/files/exercise_icons.json").readText())
            .jsonObject.getValue("icons").jsonObject.keys
        assertTrue("generic" in icons)
        parsed.exercises.forEach { exercise -> assertTrue(exercise.icon in icons, "${exercise.name}: ${exercise.icon}") }

        assertTrue(importer(file.readText()).importIfNeeded())
        assertEquals(parsed.exercises.size, catalogue.exercises().first().size)
    }
}
