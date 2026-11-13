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
    fun importsGroupsAndMachines() = runTest {
        val catalogueJson = """{"version":1,"source":{"name":"test"},
            "equipment":[{"id":"eq-machine","name":"Machine","type":"machine"}],
            "groups":[{"id":"g-press","name":"Chest press","icon":"bench_press"}],
            "machines":[{"id":"m-press","name":"Chest press machine","equipmentId":"eq-machine",
              "aliases":["seated chest press"],"description":"A seated press.","exerciseIds":["ex-incline","ex-flat"]}],
            "exercises":[
              {"id":"ex-flat","name":"Machine Chest Press","equipmentId":"eq-machine","measurementType":"weight_reps","primaryMuscles":["chest"],"secondaryMuscles":[],"instructions":[],"groupId":"g-press"},
              {"id":"ex-incline","name":"Machine Incline Chest Press","equipmentId":"eq-machine","measurementType":"weight_reps","primaryMuscles":["chest"],"secondaryMuscles":[],"instructions":[],"groupId":"g-press","source":"kinzo"}
            ]}"""
        importer(catalogueJson).importIfNeeded()

        val exercises = catalogue.exercises().first()
        assertEquals(listOf("g-press", "g-press"), exercises.map { it.groupId })
        assertEquals("Chest press", exercises.first().groupName)
        assertEquals("Chest press", catalogue.exercise("ex-incline").first()?.groupName)

        val machine = catalogue.machines().single()
        assertEquals("Chest press machine", machine.name)
        assertEquals(listOf("seated chest press"), machine.aliases)
        // The machine's exercises keep their order: the incline press first, as listed.
        assertEquals(listOf("ex-incline", "ex-flat"), machine.exerciseIds)
    }

    @Test
    fun importsAliasesAndListsVariations() = runTest {
        val catalogueJson = """{"version":1,"source":{"name":"test"},
            "equipment":[{"id":"eq-bar","name":"Barbell","type":"free_weight"}],
            "groups":[{"id":"g-ohp","name":"Overhead press","aliases":["shoulder press","military press"]}],
            "exercises":[
              {"id":"ex-ohp","name":"Overhead Press","equipmentId":"eq-bar","measurementType":"weight_reps","primaryMuscles":["shoulders"],"groupId":"g-ohp","aliases":["Military Press","Strict Press"]},
              {"id":"ex-seated","name":"Seated Overhead Press","equipmentId":"eq-bar","measurementType":"weight_reps","primaryMuscles":["shoulders"],"groupId":"g-ohp"},
              {"id":"ex-curl","name":"Barbell Curl","equipmentId":"eq-bar","measurementType":"weight_reps","primaryMuscles":["biceps"]}
            ]}"""
        importer(catalogueJson).importIfNeeded()

        val press = catalogue.exercises().first().single { it.id == "ex-ohp" }
        assertEquals(listOf("Military Press", "Strict Press"), press.aliases)
        assertEquals(listOf("shoulder press", "military press"), press.groupAliases)
        assertEquals(emptyList(), catalogue.exercises().first().single { it.id == "ex-curl" }.aliases)

        val detail = assertNotNull(catalogue.exercise("ex-ohp").first())
        assertEquals(listOf("Military Press", "Strict Press"), detail.aliases)
        assertEquals(listOf("Seated Overhead Press"), detail.variations.map { it.name })
        assertEquals(emptyList(), catalogue.exercise("ex-curl").first()?.variations)
    }

    @Test
    fun aMergedDuplicateMovesItsHistoryOntoTheExerciseKept() = runTest {
        importer(smallCatalogue(version = 1)).importIfNeeded()
        db.workoutQueries.insertWorkout(id = "w1", name = "Arms", routineId = null, startedAt = 1)
        db.workoutQueries.insertWorkoutExercise(id = "we1", workoutId = "w1", exerciseId = "ex-curl", position = 0, restS = null, now = 1)
        db.settingQueries.putSetting(key = TrendRepository.VERSION_KEY, value = "1")

        // Version 2 folds the Barbell Curl into the Bench Press, just to have two exercises.
        val merged = smallCatalogue(version = 2, includeCurl = false)
            .replace("\"exercises\":[", "\"merged\":[{\"from\":\"ex-curl\",\"to\":\"ex-bench\"}],\"exercises\":[")
        assertTrue(importer(merged).importIfNeeded())

        val logged = db.workoutQueries.workoutExercises("w1").executeAsList().single()
        assertEquals("ex-bench", logged.exercise_id)
        assertEquals("Bench Press", logged.name)
        // The trend cache rebuilds on the next launch, with the curl's sessions under the bench.
        assertEquals(null, db.settingQueries.getSetting(TrendRepository.VERSION_KEY).executeAsOneOrNull())
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

        // Groups and machines only name exercises and groups that exist.
        val exerciseIds = parsed.exercises.map { it.id }.toSet()
        val groupIds = parsed.groups.map { it.id }.toSet()
        parsed.exercises.forEach { exercise -> assertTrue(exercise.groupId == null || exercise.groupId in groupIds, exercise.name) }
        assertTrue(parsed.machines.size >= 40)
        parsed.machines.forEach { machine ->
            assertTrue(machine.exerciseIds.isNotEmpty(), machine.name)
            machine.exerciseIds.forEach { assertTrue(it in exerciseIds, "${machine.name}: $it") }
        }

        // A merge folds a dropped exercise into one still in the catalogue.
        parsed.merged.forEach { merge ->
            assertTrue(merge.to in exerciseIds, "merge into ${merge.to}")
            assertFalse(merge.from in exerciseIds, "merge from ${merge.from}")
        }
        // The names people look for are there, with the names they know them by.
        val byName = parsed.exercises.associateBy { it.name }
        assertTrue("Military Press" in byName.getValue("Overhead Press").aliases)
        assertNotNull(byName["Lat Pulldown"])
        assertNotNull(byName["Bench Press"])

        assertTrue(importer(file.readText()).importIfNeeded())
        assertEquals(parsed.exercises.size, catalogue.exercises().first().size)
        assertEquals(parsed.machines.size, catalogue.machines().size)
    }
}
