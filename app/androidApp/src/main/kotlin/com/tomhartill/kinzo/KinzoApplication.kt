package com.tomhartill.kinzo

import android.app.Application
import com.tomhartill.kinzo.data.AndroidDriverFactory
import com.tomhartill.kinzo.di.initKoin

class KinzoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(AndroidDriverFactory(this))
    }
}
