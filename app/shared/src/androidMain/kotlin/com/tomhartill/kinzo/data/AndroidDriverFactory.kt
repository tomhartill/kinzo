package com.tomhartill.kinzo.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase

class AndroidDriverFactory(private val context: Context) : DriverFactory {
    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(KinzoDatabase.Schema, context.applicationContext, DATABASE_NAME)
}
