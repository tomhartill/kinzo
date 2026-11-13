package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.SqlDriver
import com.tomhartill.kinzo.db.KinzoDatabase

/** Opens a local SQLite database by file name. Each platform supplies its own driver. */
fun interface DriverFactory {
    fun createDriver(name: String): SqlDriver
}

fun DriverFactory.createDatabase(name: String = DATABASE_NAME): KinzoDatabase = KinzoDatabase(createDriver(name))

internal const val DATABASE_NAME = "kinzo.db"

/** The demo profile's made-up history, kept apart so it never mixes with real workouts. */
internal const val DEMO_DATABASE_NAME = "kinzo-demo.db"
