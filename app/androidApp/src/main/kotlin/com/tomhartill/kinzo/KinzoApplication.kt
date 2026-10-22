package com.tomhartill.kinzo

import android.app.Application
import com.tomhartill.kinzo.di.initKoinAndroid

class KinzoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoinAndroid(this)
    }
}
