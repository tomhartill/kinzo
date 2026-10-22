package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tomhartill.kinzo.db.KinzoDatabase
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.ThemeName
import com.tomhartill.kinzo.domain.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class SettingsRepository(
    private val db: KinzoDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.settingQueries

    fun settings(): Flow<Settings> =
        queries.allSettings().asFlow().mapToList(io).map { rows ->
            val values = rows.associate { it.setting_key to it.setting_value }
            Settings(
                weightUnit = WeightUnit.fromKey(values[WEIGHT_UNIT]),
                restSeconds = values[REST_SECONDS]?.toIntOrNull() ?: Settings.DEFAULT_REST_SECONDS,
                theme = ThemeName.fromKey(values[THEME]),
                themeMode = ThemeMode.fromKey(values[THEME_MODE]),
                tipsEnabled = values[GuidanceRepository.TIPS_ENABLED] != "false",
            )
        }.distinctUntilChanged()

    suspend fun setWeightUnit(unit: WeightUnit) = put(WEIGHT_UNIT, unit.key)

    suspend fun setRestSeconds(seconds: Int) = put(REST_SECONDS, seconds.coerceAtLeast(0).toString())

    suspend fun setTheme(theme: ThemeName) = put(THEME, theme.key)

    suspend fun setThemeMode(mode: ThemeMode) = put(THEME_MODE, mode.key)

    suspend fun setTipsEnabled(enabled: Boolean) = put(GuidanceRepository.TIPS_ENABLED, enabled.toString())

    private suspend fun put(key: String, value: String) = withContext(io) {
        queries.putSetting(key = key, value = value)
    }

    private companion object {
        const val WEIGHT_UNIT = "weight_unit"
        const val REST_SECONDS = "rest_seconds"
        const val THEME = "appearance.theme"
        const val THEME_MODE = "appearance.mode"
    }
}
