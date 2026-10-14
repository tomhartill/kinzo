package com.tomhartill.kinzo.di

import android.app.Application
import com.tomhartill.kinzo.account.ActivityTracker
import com.tomhartill.kinzo.account.AndroidBrowserSignIn
import com.tomhartill.kinzo.account.AndroidGoogleSignIn
import com.tomhartill.kinzo.account.AndroidSharer
import com.tomhartill.kinzo.account.BrowserSignIn
import com.tomhartill.kinzo.account.NativeGoogleSignIn
import com.tomhartill.kinzo.account.Sharer
import com.tomhartill.kinzo.data.AndroidDriverFactory
import org.koin.dsl.module

/** Called from the Application's onCreate, before any activity exists. */
fun initKoinAndroid(application: Application) = initKoin(
    AndroidDriverFactory(application),
    module {
        // Created now, so it sees the first activity open.
        single(createdAtStart = true) { ActivityTracker(application) }
        single<BrowserSignIn> { AndroidBrowserSignIn(get()) }
        single<NativeGoogleSignIn> { AndroidGoogleSignIn(get()) }
        single<Sharer> { AndroidSharer(get()) }
    },
)
