package com.tomhartill.kinzo.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun SystemBarsAppearance(dark: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    LaunchedEffect(activity, dark) {
        // The same scrims enableEdgeToEdge uses by default; they only show with three-button
        // navigation on older phones.
        val navigation = if (dark) {
            SystemBarStyle.dark(DARK_SCRIM)
        } else {
            SystemBarStyle.light(LIGHT_SCRIM, DARK_SCRIM)
        }
        val status = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = navigation)
    }
}

private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
