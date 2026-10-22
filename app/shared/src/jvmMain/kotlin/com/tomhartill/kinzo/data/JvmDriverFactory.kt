package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase
import java.io.File
import java.util.Properties

/**
 * Desktop (development) database, stored in ~/.kinzo by default. Pass
 * [JdbcSqliteDriver.IN_MEMORY] for a throwaway database in tests.
 */
class JvmDriverFactory(private val url: String = defaultUrl()) : DriverFactory {
    override fun createDriver(): SqlDriver = JdbcSqliteDriver(url, Properties(), KinzoDatabase.Schema)

    private companion object {
        fun defaultUrl(): String {
            val directory = File(System.getProperty("user.home"), ".kinzo").apply { mkdirs() }
            return "jdbc:sqlite:${File(directory, DATABASE_NAME).absolutePath}"
        }
    }
}
