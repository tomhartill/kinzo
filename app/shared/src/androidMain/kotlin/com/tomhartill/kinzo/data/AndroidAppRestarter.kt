package com.tomhartill.kinzo.data

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/** Reopens Kinzo in a fresh process, so every screen and database starts again. */
class AndroidAppRestarter(private val context: Context) : AppRestarter {
    override fun restart(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return false
        context.startActivity(Intent.makeRestartActivityTask(launch))
        exitProcess(0)
    }
}
