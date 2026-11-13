package com.tomhartill.kinzo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tomhartill.kinzo.db.KinzoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * What someone has been shown: the first-run introduction and the first-time tips. Kept in the
 * phone's settings table and never synced, so a new phone shows them again, which is fine.
 */
class GuidanceRepository(
    private val db: KinzoDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val queries get() = db.settingQueries

    /** The tips dismissed so far, and whether tips are on. */
    fun tips(): Flow<TipsSeen> =
        queries.allSettings().asFlow().mapToList(io).map { rows ->
            TipsSeen(
                seen = rows.mapNotNullTo(mutableSetOf()) { row ->
                    row.setting_key.takeIf { it.startsWith(SEEN_PREFIX) }?.removePrefix(SEEN_PREFIX)
                },
                enabled = rows.firstOrNull { it.setting_key == TIPS_ENABLED }?.setting_value != "false",
            )
        }.distinctUntilChanged()

    suspend fun markTipSeen(id: String) = withContext(io) {
        queries.putSetting(key = SEEN_PREFIX + id, value = "1")
    }

    /** Every tip shows again, once each. */
    suspend fun resetTips() = withContext(io) {
        queries.deleteSettingsWithPrefix(SEEN_PREFIX)
    }

    /**
     * The newest introduction this person has finished, or 0 for none. Installs from before the
     * introduction existed already know the basics, so they count as having seen version 1.
     * Call it before the exercise library import runs, which is what marks an install as old.
     */
    suspend fun introductionVersion(): Int = withContext(io) {
        val stored = queries.getSetting(INTRODUCTION_VERSION).executeAsOneOrNull()?.toIntOrNull()
        when {
            stored != null -> stored
            queries.getSetting(CatalogueImporter.VERSION_KEY).executeAsOneOrNull() != null -> {
                queries.putSetting(key = INTRODUCTION_VERSION, value = "1")
                1
            }
            else -> 0
        }
    }

    suspend fun setIntroductionVersion(version: Int) = withContext(io) {
        queries.putSetting(key = INTRODUCTION_VERSION, value = version.toString())
    }

    data class TipsSeen(val seen: Set<String>, val enabled: Boolean)

    internal companion object {
        const val TIPS_ENABLED = "tips.enabled"
        const val SEEN_PREFIX = "tip.seen."
        const val INTRODUCTION_VERSION = "introduction.version"
    }
}
