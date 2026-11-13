package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.Introduction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoProfileTest {
    private val clock = FakeClock()

    @Test
    fun demoHistoryUsesExercisesFromTheBundledLibrary() = runTest {
        val db = inMemoryDatabase()
        val catalogue = File("src/commonMain/composeResources/files/catalogue.json").readText()
        CatalogueImporter(db, clock, Dispatchers.Unconfined) { catalogue }.importIfNeeded()
        DemoHistory.seed(db, clock.millis)

        val workouts = db.workoutQueries.finishedWorkouts().executeAsList()
        // Four sessions a week, less the odd missed one.
        assertTrue(workouts.size in 45..DemoHistory.WEEKS * 4, "got ${workouts.size} workouts")
        assertTrue(workouts.all { it.started_at < clock.millis })
        // Every exercise is in the library, so each one shows by name.
        val rows = db.workoutQueries.finishedWorkoutExercises().executeAsList()
        assertEquals(workouts.size * 5 - workouts.count { it.name.endsWith("run") } * 4, rows.size)
        assertTrue(rows.all { it.set_count > 0 })
    }

    @Test
    fun liftsGetHeavierOverTheBlock() {
        val db = inMemoryDatabase()
        DemoHistory.seed(db, clock.millis)
        val bench = db.workoutQueries.exerciseHistory("1a543f99-227d-5eb1-9178-a1d92b0b9cbe").executeAsList()
            .filter { it.is_warmup == 0L }
        val first = bench.last().weight_kg!!
        val last = bench.first().weight_kg!!
        assertTrue(last - first >= 15.0, "bench went from $first to $last")
    }

    @Test
    fun switchingKeepsTheRealDatabaseApart() = runTest {
        val directory = Files.createTempDirectory("kinzo-demo").toFile()
        val factory = DriverFactory { name -> JvmDriverFactory("jdbc:sqlite:${File(directory, name).absolutePath}").createDriver(name) }
        val restarter = AppRestarter { false }

        val before = DemoProfile(factory, restarter, clock)
        assertFalse(before.isActive)
        before.openActiveDatabase().settingQueries.putSetting(key = "appearance.theme", value = "sage")
        assertFalse(before.switchTo(demo = true))

        val demo = DemoProfile(factory, restarter, clock)
        assertTrue(demo.isActive)
        val demoDb = demo.openActiveDatabase()
        assertTrue(demoDb.workoutQueries.finishedWorkouts().executeAsList().isNotEmpty())
        assertEquals("sage", demoDb.settingQueries.getSetting("appearance.theme").executeAsOneOrNull())
        assertEquals(Introduction.VERSION.toString(), demoDb.settingQueries.getSetting("introduction.version").executeAsOneOrNull())

        demo.switchTo(demo = false)
        val after = DemoProfile(factory, restarter, clock)
        assertFalse(after.isActive)
        assertTrue(after.openActiveDatabase().workoutQueries.finishedWorkouts().executeAsList().isEmpty())
        directory.deleteRecursively()
    }
}
