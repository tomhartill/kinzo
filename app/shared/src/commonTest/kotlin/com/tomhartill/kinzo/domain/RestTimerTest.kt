package com.tomhartill.kinzo.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerTest {
    /** Time the phone spent asleep: the wall clock moves on but coroutine delays don't. */
    private var sleptMillis = 0L

    private fun TestScope.timer() = RestTimer(
        clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime + sleptMillis)
        },
        scope = backgroundScope,
    )

    @Test
    fun endsOnTimeAfterThePhoneSleeps() = runTest {
        val timer = timer()
        timer.start(90)
        advanceTimeBy(10_000)
        sleptMillis = 120_000
        advanceTimeBy(1_001)
        assertNull(timer.state.value)
    }

    @Test
    fun countsDownAndAnnouncesTheEnd() = runTest {
        val timer = timer()
        val finished = async { timer.finished.first() }
        runCurrent()
        timer.start(90)
        assertEquals(90, timer.state.value?.totalSeconds)
        advanceTimeBy(89_000)
        assertEquals(1_000, timer.remainingMillis())
        advanceTimeBy(1_001)
        assertNull(timer.state.value)
        finished.await()
    }

    @Test
    fun adjustingMovesTheEnd() = runTest {
        val timer = timer()
        timer.start(60)
        timer.adjust(15)
        assertEquals(75_000, timer.remainingMillis())
        timer.adjust(-30)
        assertEquals(45_000, timer.remainingMillis())
        timer.adjust(-60)
        assertNull(timer.state.value)
    }

    @Test
    fun skippingStopsWithoutAnnouncing() = runTest {
        val timer = timer()
        var announced = false
        val watcher = async { timer.finished.first(); announced = true }
        runCurrent()
        timer.start(30)
        assertNotNull(timer.state.value)
        timer.skip()
        advanceTimeBy(60_000)
        assertNull(timer.state.value)
        assertTrue(!announced)
        watcher.cancel()
    }

    @Test
    fun zeroSecondsMeansNoTimer() = runTest {
        val timer = timer()
        timer.start(0)
        assertNull(timer.state.value)
    }
}
