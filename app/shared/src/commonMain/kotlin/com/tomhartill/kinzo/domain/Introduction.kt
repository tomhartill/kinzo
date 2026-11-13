package com.tomhartill.kinzo.domain

/** The pages of the first-run introduction, in order. */
enum class IntroductionStep(
    /** The introduction version that added this page. */
    val sinceVersion: Int,
) {
    Welcome(1),
    Units(1),
    Rest(1),
    Theme(1),
    Ready(1),
}

object Introduction {
    /**
     * Bump this when a release adds a page worth showing to people who've already been through
     * the introduction, and give that page the new number. They then see only the new pages.
     */
    const val VERSION = 1

    /** The rest times offered on the rest page; the full list stays in Profile. */
    val REST_CHOICES = listOf(0, 60, 90, 120, 180)

    /** What to show someone who has finished [seenVersion]: nothing once they're up to date. */
    fun stepsAfter(seenVersion: Int): List<IntroductionStep> {
        val new = IntroductionStep.entries.filter { it.sinceVersion > seenVersion }
        // Someone coming back for one new page doesn't need welcoming again, just the new page.
        return if (seenVersion == 0) new else new.filterNot { it == IntroductionStep.Welcome || it == IntroductionStep.Ready }
    }
}
