package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.Introduction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * A second profile full of made-up workouts, for trying the progress charts and trends without
 * logging months of training first. It is a separate database file, so nothing in it ever
 * touches real workouts, and the rest of the app doesn't know which one it has: the choice is
 * made once at startup, and switching restarts the app.
 *
 * The switch itself is stored in the real database, which always exists.
 */
class DemoProfile(
    private val driverFactory: DriverFactory,
    private val restarter: AppRestarter,
    private val clock: Clock,
) {
    private val main: KinzoDatabase by lazy { driverFactory.createDatabase() }

    /** Read once: the app runs on one database from start to finish. */
    val isActive: Boolean by lazy { main.settingQueries.getSetting(ACTIVE_KEY).executeAsOneOrNull() == "true" }

    /** The database the app runs on this time. */
    fun openActiveDatabase(): KinzoDatabase = if (isActive) driverFactory.createDatabase(DEMO_DATABASE_NAME) else main

    /**
     * Switches profile and restarts the app. The first switch to the demo fills it with history.
     * Returns false if the app couldn't restart itself and has to be reopened by hand.
     */
    suspend fun switchTo(demo: Boolean): Boolean {
        withContext(Dispatchers.IO) {
            if (demo) prepareDemo()
            main.settingQueries.putSetting(key = ACTIVE_KEY, value = demo.toString())
        }
        return restarter.restart()
    }

    private fun prepareDemo() {
        val demoDriver = driverFactory.createDriver(DEMO_DATABASE_NAME)
        try {
            val demo = KinzoDatabase(demoDriver)
            if (demo.workoutQueries.finishedWorkouts().executeAsList().isEmpty()) {
                DemoHistory.seed(demo, clock.now().toEpochMilliseconds())
            }
            copyPreferences(demo)
        } finally {
            demoDriver.close()
        }
    }

    /**
     * Units, theme and which tips have been seen follow you into the demo, and its introduction
     * counts as seen. Nothing else does: not the account, and not the import markers, so the
     * demo imports its own exercise library on its first start.
     */
    private fun copyPreferences(demo: KinzoDatabase) {
        val shared = setOf(
            SettingsRepository.WEIGHT_UNIT,
            SettingsRepository.REST_SECONDS,
            SettingsRepository.THEME,
            SettingsRepository.THEME_MODE,
            GuidanceRepository.TIPS_ENABLED,
        )
        demo.transaction {
            main.settingQueries.allSettings().executeAsList()
                .filter { it.setting_key in shared || it.setting_key.startsWith(GuidanceRepository.SEEN_PREFIX) }
                .forEach { demo.settingQueries.putSetting(key = it.setting_key, value = it.setting_value) }
            demo.settingQueries.putSetting(key = GuidanceRepository.INTRODUCTION_VERSION, value = Introduction.VERSION.toString())
        }
    }

    private companion object {
        const val ACTIVE_KEY = "profile.demo"
    }
}
