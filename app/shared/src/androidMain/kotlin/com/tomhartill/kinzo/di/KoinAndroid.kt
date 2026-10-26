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
import com.tomhartill.kinzo.photos.AndroidCamera
import com.tomhartill.kinzo.photos.Camera
import com.tomhartill.kinzo.photos.FilePhotoFiles
import com.tomhartill.kinzo.photos.PhotoFiles
import com.tomhartill.kinzo.data.AndroidAppRestarter
import com.tomhartill.kinzo.data.AppRestarter
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
        single<Camera> { AndroidCamera(application, get()) }
        single<PhotoFiles> { FilePhotoFiles(application.filesDir.resolve("photos")) }
        single<AppRestarter> { AndroidAppRestarter(application) }
    },
)
