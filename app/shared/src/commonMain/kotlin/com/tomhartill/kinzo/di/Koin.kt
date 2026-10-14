package com.tomhartill.kinzo.di

import org.koin.core.context.startKoin
import org.koin.dsl.module

// Repositories and view models are registered here as features land.
val appModule = module {
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
