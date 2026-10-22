package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.SqlDriver
import com.tomhartill.kinzo.db.KinzoDatabase

/** Opens the local SQLite database. Each platform supplies its own driver. */
fun interface DriverFactory {
    fun createDriver(): SqlDriver
}

fun DriverFactory.createDatabase(): KinzoDatabase = KinzoDatabase(createDriver())

internal const val DATABASE_NAME = "kinzo.db"
