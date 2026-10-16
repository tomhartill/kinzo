package com.tomhartill.kinzo.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The countdown between sets. It lives for the whole app, so leaving the workout screen
 * doesn't stop it. State holds the end time rather than a ticking count, so the UI can
 * redraw as often as it likes without drift.
 */
class RestTimer(private val clock: Clock, private val scope: CoroutineScope) {
    data class State(val endsAtMillis: Long, val totalSeconds: Int)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a countdown reaches zero on its own (not when it is skipped). */
    val finished: SharedFlow<Unit> = _finished

    private var job: Job? = null

    fun start(seconds: Int) {
        if (seconds <= 0) {
            skip()
            return
        }
        set(State(endsAtMillis = now() + seconds * 1000L, totalSeconds = seconds))
    }

    /** Adds or removes time, for the +15 s and −15 s buttons. Going below zero ends the rest. */
    fun adjust(deltaSeconds: Int) {
        val current = _state.value ?: return
        val endsAt = current.endsAtMillis + deltaSeconds * 1000L
        if (endsAt <= now()) {
            skip()
        } else {
            set(State(endsAtMillis = endsAt, totalSeconds = (current.totalSeconds + deltaSeconds).coerceAtLeast(1)))
        }
    }

    fun skip() {
        job?.cancel()
        job = null
        _state.value = null
    }

    fun remainingMillis(): Long = _state.value?.let { (it.endsAtMillis - now()).coerceAtLeast(0) } ?: 0

    private fun set(state: State) {
        job?.cancel()
        _state.value = state
        job = scope.launch {
            // Short steps against the wall clock, not one long delay: delays don't count time
            // the phone spends asleep, which would leave the bar stuck at 0:00 after unlocking.
            while (true) {
                val left = state.endsAtMillis - now()
                if (left <= 0) break
                delay(minOf(left, 1_000))
            }
            if (_state.value == state) {
                _state.value = null
                _finished.tryEmit(Unit)
            }
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()
}
