package com.tomhartill.kinzo.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tomhartill.kinzo.db.KinzoDatabase
import kotlin.time.Clock
import kotlin.time.Instant

internal fun inMemoryDatabase(): KinzoDatabase = JvmDriverFactory(JdbcSqliteDriver.IN_MEMORY).createDatabase()

/** A clock the test moves by hand. */
internal class FakeClock(var millis: Long = 1_760_000_000_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)

    fun advance(by: Long) {
        millis += by
    }
}

/** A two-exercise catalogue: enough to log a workout against. */
internal fun smallCatalogue(version: Int = 1, includeCurl: Boolean = true): String {
    val curl = if (includeCurl) {
        """,{"id":"ex-curl","name":"Barbell Curl","equipmentId":"eq-bar","measurementType":"weight_reps","primaryMuscles":["biceps"],"secondaryMuscles":["forearms"],"instructions":["Curl."],"sourceRef":"Barbell_Curl"}"""
    } else {
        ""
    }
    return """{"version":$version,"source":{"name":"test"},
        "equipment":[{"id":"eq-bar","name":"Barbell","type":"free_weight"},{"id":"eq-body","name":"Bodyweight","type":"bodyweight"}],
        "exercises":[
          {"id":"ex-bench","name":"Bench Press","equipmentId":"eq-bar","measurementType":"weight_reps","primaryMuscles":["chest"],"secondaryMuscles":["shoulders","triceps"],"instructions":["Lie on the bench.","Press the bar."],"sourceRef":"Bench_Press","icon":"bench_press"},
          {"id":"ex-plank","name":"Plank","equipmentId":"eq-body","measurementType":"duration","primaryMuscles":["abdominals"],"secondaryMuscles":[],"instructions":["Hold."],"sourceRef":"Plank"}
          $curl
        ]}"""
}
