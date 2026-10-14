package com.tomhartill.kinzo.di

import com.tomhartill.kinzo.account.BrowserSignIn
import com.tomhartill.kinzo.account.IosBrowserSignIn
import com.tomhartill.kinzo.account.IosSharer
import com.tomhartill.kinzo.account.NativeGoogleSignIn
import com.tomhartill.kinzo.account.NoNativeGoogleSignIn
import com.tomhartill.kinzo.account.Sharer
import com.tomhartill.kinzo.data.IosDriverFactory
import org.koin.dsl.module

/** Swift entry point: `KoinIosKt.doInitKoinIos()`. */
fun initKoinIos() = initKoin(
    IosDriverFactory(),
    module {
        single<BrowserSignIn> { IosBrowserSignIn() }
        single<NativeGoogleSignIn> { NoNativeGoogleSignIn }
        single<Sharer> { IosSharer() }
    },
)
