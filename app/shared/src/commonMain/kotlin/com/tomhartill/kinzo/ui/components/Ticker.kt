package com.tomhartill.kinzo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlin.time.Clock

/** The current time in epoch milliseconds, refreshed every [intervalMillis] while on screen. */
@Composable
fun rememberNow(intervalMillis: Long = 1_000): State<Long> =
    produceState(initialValue = Clock.System.now().toEpochMilliseconds(), intervalMillis) {
        while (true) {
            value = Clock.System.now().toEpochMilliseconds()
            // Wake just after the next whole interval so clocks tick on the second.
            delay(intervalMillis - value % intervalMillis + 5)
        }
    }
