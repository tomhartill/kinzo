package com.tomhartill.kinzo.ui.theme

import androidx.compose.runtime.Composable

// iOS picks the status bar style from the view controller, which follows the phone's mode.
// Matching a forced Light or Dark choice needs the Swift side to read it; not done yet.
@Composable
internal actual fun SystemBarsAppearance(dark: Boolean) = Unit
