package com.tomhartill.kinzo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipQueueTest {
    private val intro = Tip("a.intro", "A", "Intro", Tip.Kind.Page)
    private val first = Tip("a.first", "First", "One", Tip.Kind.Spotlight)
    private val second = Tip("a.second", "Second", "Two", Tip.Kind.Spotlight)
    private val screenA = ScreenTips("a", intro, listOf(first, second))
    private val screenB = ScreenTips("b", Tip("b.intro", "B", "Intro", Tip.Kind.Page))
    private val ready = TipQueue().loaded(seen = emptySet(), enabled = true)

    @Test
    fun showsAScreensTipsOneAtATimeInOrder() {
        var queue = ready.visit(screenA)
        assertEquals(intro, queue.current)
        assertTrue(queue.hasNext)
        queue = queue.dismiss(intro)
        assertEquals(first, queue.current)
        queue = queue.dismiss(first).dismiss(second)
        assertNull(queue.current)
        assertEquals(setOf(intro.id, first.id, second.id), queue.seen)
    }

    @Test
    fun nothingShowsUntilWhatsBeenSeenIsKnown() {
        val waiting = TipQueue().visit(screenA)
        assertNull(waiting.current)
        // Seen tips that were waiting drop out once the seen list arrives.
        assertEquals(first, waiting.loaded(seen = setOf(intro.id), enabled = true).current)
    }

    @Test
    fun seenTipsAndSwitchedOffTipsDontShow() {
        assertEquals(second, ready.copy(seen = setOf(intro.id, first.id)).visit(screenA).current)
        assertNull(ready.loaded(seen = emptySet(), enabled = false).visit(screenA).current)
    }

    @Test
    fun anotherScreenReplacesWhatsWaiting() {
        val queue = ready.visit(screenA).visit(screenB)
        assertEquals(screenB.intro, queue.current)
        assertFalse(queue.hasNext)
        // Screen A closing late doesn't clear screen B's tips.
        assertEquals(screenB.intro, queue.leave("a").current)
        assertNull(queue.leave("b").current)
    }

    @Test
    fun skippedTipsStayUnseen() {
        val queue = ready.visit(ScreenTips("a", spotlights = listOf(first, second))).skip(first)
        assertEquals(second, queue.current)
        assertFalse(first.id in queue.seen)
    }

    @Test
    fun replayShowsSeenTipsEvenWithTipsOff() {
        val allSeen = ready.loaded(seen = setOf(intro.id, first.id, second.id), enabled = false)
        assertNull(allSeen.visit(screenA).current)
        var replay = allSeen.replay(screenA)
        assertEquals(intro, replay.current)
        replay = replay.loaded(seen = replay.seen, enabled = false)
        assertEquals(intro, replay.current)
        replay = replay.dismiss(intro).dismiss(first).dismiss(second)
        assertNull(replay.current)
        assertFalse(replay.replaying)
    }
}
