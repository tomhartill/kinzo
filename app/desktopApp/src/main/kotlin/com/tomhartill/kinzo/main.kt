package com.tomhartill.kinzo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tomhartill.kinzo.data.JvmDriverFactory
import com.tomhartill.kinzo.di.initKoin

// Desktop is a development target only: a phone-sized window for quick UI iteration.
fun main() {
    initKoin(JvmDriverFactory())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Kinzo",
            state = rememberWindowState(size = DpSize(400.dp, 820.dp)),
        ) {
            App()
        }
    }
}
