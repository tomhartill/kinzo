package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase

class IosDriverFactory : DriverFactory {
    override fun createDriver(): SqlDriver = NativeSqliteDriver(KinzoDatabase.Schema, DATABASE_NAME)
}
