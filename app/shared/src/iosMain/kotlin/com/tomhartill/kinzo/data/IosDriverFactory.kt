package com.tomhartill.kinzo.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase

class IosDriverFactory : DriverFactory {
    override fun createDriver(name: String): SqlDriver = NativeSqliteDriver(KinzoDatabase.Schema, name)
}
