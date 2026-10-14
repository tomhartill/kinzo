package com.tomhartill.kinzo

import android.app.Application
import com.tomhartill.kinzo.di.initKoin

class KinzoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
