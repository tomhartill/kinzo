package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Phones keep their database across app updates, so each schema change ships as a migration
 * (src/commonMain/sqldelight/migrations). These tests build a database the way an older
 * version of the app did, open it with the current schema, and check it ends up the same as
 * a fresh install, with the logged data intact.
 */
class MigrationTest {
    private val file = File.createTempFile("kinzo-migration", ".db")
    private val url = "jdbc:sqlite:${file.absolutePath}"

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun version1UpgradesToTheCurrentSchema() {
        JdbcSqliteDriver(url, Properties()).use { driver ->
            VERSION_1_SCHEMA.forEach { driver.execute(null, it, 0) }
            driver.execute(null, "INSERT INTO workout VALUES ('w1', 'Push', NULL, 1, 2, NULL, 2, NULL)", 0)
            driver.execute(null, "INSERT INTO workout_exercise VALUES ('we1', 'w1', 'ex-bench', 0, 2, NULL)", 0)
            driver.execute(null, "INSERT INTO workout_set VALUES ('s1', 'we1', 0, 5, 60.0, NULL, NULL, NULL, NULL, 0, NULL, 2, 2, NULL)", 0)
            driver.execute(null, "PRAGMA user_version = 1", 0)
        }

        val upgraded = JdbcSqliteDriver(url, Properties(), KinzoDatabase.Schema)
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), KinzoDatabase.Schema)
        try {
            assertEquals(KinzoDatabase.Schema.version, upgraded.userVersion())
            assertEquals(fresh.schemaShape(), upgraded.schemaShape())
            val set = KinzoDatabase(upgraded).workoutQueries.workoutSets("w1").executeAsList().single()
            assertEquals(5L, set.reps)
            assertEquals(60.0, set.weight_kg)
        } finally {
            upgraded.close()
            fresh.close()
        }
    }

    @Test
    fun workoutsSavedBeforeVersion4AreWeightTraining() {
        JdbcSqliteDriver(url, Properties()).use { driver ->
            VERSION_1_SCHEMA.forEach { driver.execute(null, it, 0) }
            KinzoDatabase.Schema.migrate(driver, 1, 3)
            driver.execute(null, "INSERT INTO routine VALUES ('r1', 'Push', NULL, 'user', 1, 2, NULL)", 0)
            driver.execute(null, "PRAGMA user_version = 3", 0)
        }

        val upgraded = JdbcSqliteDriver(url, Properties(), KinzoDatabase.Schema)
        try {
            val routine = KinzoDatabase(upgraded).routineQueries.routineById("r1").executeAsOne()
            assertEquals("Push", routine.name)
            assertEquals("resistance", routine.workout_type)
            assertNull(routine.split_id)
            assertNull(routine.mesocycle_id)
        } finally {
            upgraded.close()
        }
    }

    private fun SqlDriver.userVersion(): Long =
        executeQuery(null, "PRAGMA user_version", { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) }, 0).value!!

    /** Every table's columns (name, type, not-null) and every index's columns, sorted. */
    private fun SqlDriver.schemaShape(): List<String> {
        val objects = executeQuery(
            null,
            "SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name",
            { cursor ->
                val rows = mutableListOf<Pair<String, String>>()
                while (cursor.next().value) rows += cursor.getString(0)!! to cursor.getString(1)!!
                QueryResult.Value(rows)
            },
            0,
        ).value
        return objects.flatMap { (type, name) ->
            val pragma = if (type == "table") "PRAGMA table_info('$name')" else "PRAGMA index_info('$name')"
            executeQuery(
                null,
                pragma,
                { cursor ->
                    val columns = mutableListOf<String>()
                    while (cursor.next().value) {
                        columns += if (type == "table") {
                            "$type $name: ${cursor.getString(1)} ${cursor.getString(2)} notnull=${cursor.getLong(3)}"
                        } else {
                            "$type $name: ${cursor.getString(2)}"
                        }
                    }
                    QueryResult.Value(columns)
                },
                0,
            ).value
        }.sorted()
    }

    private companion object {
        /** The schema as first shipped (commit 7350188), before any migration. */
        val VERSION_1_SCHEMA = listOf(
            """CREATE TABLE equipment (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, description TEXT, setup_instructions TEXT, source TEXT NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)""",
            """CREATE TABLE exercise (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT, instructions TEXT NOT NULL, equipment_id TEXT, measurement_type TEXT NOT NULL, source TEXT NOT NULL, source_ref TEXT, updated_at INTEGER NOT NULL, deleted_at INTEGER)""",
            """CREATE TABLE exercise_muscle (exercise_id TEXT NOT NULL, muscle TEXT NOT NULL, is_primary INTEGER NOT NULL, PRIMARY KEY (exercise_id, muscle))""",
            """CREATE TABLE setting (setting_key TEXT NOT NULL PRIMARY KEY, setting_value TEXT NOT NULL)""",
            """CREATE TABLE workout (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, routine_id TEXT, started_at INTEGER NOT NULL, ended_at INTEGER, notes TEXT, updated_at INTEGER NOT NULL, deleted_at INTEGER)""",
            """CREATE INDEX workout_started_at ON workout(started_at)""",
            """CREATE TABLE workout_exercise (id TEXT NOT NULL PRIMARY KEY, workout_id TEXT NOT NULL, exercise_id TEXT NOT NULL, position INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)""",
            """CREATE INDEX workout_exercise_workout ON workout_exercise(workout_id, position)""",
            """CREATE INDEX workout_exercise_exercise ON workout_exercise(exercise_id)""",
            """CREATE TABLE workout_set (id TEXT NOT NULL PRIMARY KEY, workout_exercise_id TEXT NOT NULL, position INTEGER NOT NULL, reps INTEGER, weight_kg REAL, duration_s INTEGER, distance_m REAL, rpe REAL, pain_score INTEGER, is_warmup INTEGER NOT NULL DEFAULT 0, note TEXT, completed_at INTEGER, updated_at INTEGER NOT NULL, deleted_at INTEGER)""",
            """CREATE INDEX workout_set_exercise ON workout_set(workout_exercise_id, position)""",
        )
    }
}
