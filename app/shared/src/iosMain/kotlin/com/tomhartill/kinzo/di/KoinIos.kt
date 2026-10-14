package com.tomhartill.kinzo.di

import com.tomhartill.kinzo.data.IosDriverFactory

/** Swift entry point: `KoinIosKt.doInitKoinIos()`. */
fun initKoinIos() = initKoin(IosDriverFactory())
