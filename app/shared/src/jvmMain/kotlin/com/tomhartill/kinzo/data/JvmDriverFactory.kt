package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase
import java.io.File
import java.util.Properties

/**
 * Desktop (development) databases, stored in ~/.kinzo by default. Pass a [url] such as
 * [JdbcSqliteDriver.IN_MEMORY] to open that instead, whatever the name: a throwaway database in
 * tests.
 */
class JvmDriverFactory(private val url: String? = null) : DriverFactory {
    override fun createDriver(name: String): SqlDriver =
        JdbcSqliteDriver(url ?: defaultUrl(name), Properties(), KinzoDatabase.Schema)

    private companion object {
        fun defaultUrl(name: String): String {
            val directory = File(System.getProperty("user.home"), ".kinzo").apply { mkdirs() }
            return "jdbc:sqlite:${File(directory, name).absolutePath}"
        }
    }
}
