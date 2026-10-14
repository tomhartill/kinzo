package com.tomhartill.kinzo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Handles the system back gesture or button (Android back, iOS edge swipe) while [enabled].
 * Without one, back on a sub-screen falls through to the platform and leaves the app.
 *
 * Call it unconditionally and drive it with [enabled], so handler order stays stable.
 */
@Composable
fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Previews and hosts without a dispatcher have nothing to register with.
    if (LocalNavigationEventDispatcherOwner.current == null) return
    NavigationEventHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isForwardEnabled = false,
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}
