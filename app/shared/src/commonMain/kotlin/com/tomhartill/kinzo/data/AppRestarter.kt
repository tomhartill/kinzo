package com.tomhartill.kinzo.data

/** Closes and reopens the app, for a change that needs a fresh start, such as switching profile. */
fun interface AppRestarter {
    /** False where the app can't reopen itself, so the person has to. */
    fun restart(): Boolean
}
