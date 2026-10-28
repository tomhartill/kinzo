package com.tomhartill.kinzo.di

import com.tomhartill.kinzo.account.BrowserSignIn
import com.tomhartill.kinzo.account.DesktopBrowserSignIn
import com.tomhartill.kinzo.account.DesktopSharer
import com.tomhartill.kinzo.account.NativeGoogleSignIn
import com.tomhartill.kinzo.account.NoNativeGoogleSignIn
import com.tomhartill.kinzo.account.Sharer
import com.tomhartill.kinzo.data.JvmDriverFactory
import com.tomhartill.kinzo.photos.Camera
import com.tomhartill.kinzo.photos.DesktopCamera
import com.tomhartill.kinzo.photos.FilePhotoFiles
import com.tomhartill.kinzo.photos.PhotoFiles
import com.tomhartill.kinzo.data.AppRestarter
import org.koin.dsl.module

/** The desktop app's startup. */
fun initKoinDesktop() = initKoin(
    JvmDriverFactory(),
    module {
        single<BrowserSignIn> { DesktopBrowserSignIn() }
        single<NativeGoogleSignIn> { NoNativeGoogleSignIn }
        single<Sharer> { DesktopSharer() }
        single<Camera> { DesktopCamera() }
        single<PhotoFiles> { FilePhotoFiles() }
        // Development only: run it again to switch profile.
        single<AppRestarter> { AppRestarter { false } }
    },
)
