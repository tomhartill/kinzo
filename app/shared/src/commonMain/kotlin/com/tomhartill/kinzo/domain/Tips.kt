package com.tomhartill.kinzo.domain

/**
 * A first-time tip. [id] is stored once the tip is dismissed, so it never changes for the same
 * tip: rewording one doesn't show it again, but giving it a new id does (for a tip whose meaning
 * changed). Ids never contain `%` or `_`, which the reset query would read as wildcards.
 */
data class Tip(val id: String, val title: String, val body: String, val kind: Kind) {
    enum class Kind {
        /** A card at the top of a screen. */
        Page,

        /** Dims the screen and points at one control. */
        Spotlight,
    }
}

/** One screen's tips: an optional page card, then spotlights, shown in that order. */
data class ScreenTips(val screen: String, val intro: Tip? = null, val spotlights: List<Tip> = emptyList()) {
    val all: List<Tip> get() = listOfNotNull(intro) + spotlights
}

/**
 * Decides which tip shows. One at a time app-wide, only for the screen showing now, at most one
 * run through a screen's tips per visit, and never one already seen unless asked for again.
 * Plain logic, so the rules are tested without any UI.
 */
data class TipQueue(
    /** Nothing shows until what's been seen is known, so a seen tip never flashes up. */
    val loaded: Boolean = false,
    val seen: Set<String> = emptySet(),
    val enabled: Boolean = true,
    /** The screen whose tips are waiting. */
    val screen: String? = null,
    val waiting: List<Tip> = emptyList(),
    /** Asked for again with a screen's tips button: shows even seen tips, or with tips off. */
    val replaying: Boolean = false,
) {
    val current: Tip? get() = if (loaded && (enabled || replaying)) waiting.firstOrNull() else null

    /** Whether another tip follows the current one, so its button says Next. */
    val hasNext: Boolean get() = current != null && waiting.size > 1

    fun loaded(seen: Set<String>, enabled: Boolean): TipQueue = copy(
        loaded = true,
        seen = seen,
        enabled = enabled,
        waiting = if (replaying) waiting else waiting.filterNot { it.id in seen },
    )

    /** A screen opened: its unseen tips replace whatever another screen left waiting. */
    fun visit(tips: ScreenTips): TipQueue =
        copy(screen = tips.screen, waiting = tips.all.filterNot { it.id in seen }, replaying = false)

    /** Every tip for the screen again, seen or not. */
    fun replay(tips: ScreenTips): TipQueue = copy(screen = tips.screen, waiting = tips.all, replaying = true)

    /** The screen closed. Its tips that didn't show yet wait for the next visit. */
    fun leave(screen: String): TipQueue =
        if (this.screen == screen) copy(screen = null, waiting = emptyList(), replaying = false) else this

    /** Dismissed: never shown again unless asked for. */
    fun dismiss(tip: Tip): TipQueue {
        val rest = waiting - tip
        return copy(seen = seen + tip.id, waiting = rest, replaying = replaying && rest.isNotEmpty())
    }

    /** Couldn't show (its control isn't on screen): left unseen, for another visit. */
    fun skip(tip: Tip): TipQueue = copy(waiting = waiting - tip)
}
