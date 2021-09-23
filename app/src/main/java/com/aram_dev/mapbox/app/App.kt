package com.aram_dev.mapbox.app

import am.ggtaxi.main.ggdriver.main.app.initTimber
import android.app.Application
import android.widget.Toast

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        initTimber()
    }

    override fun onTerminate() {
        super.onTerminate()
        Toast.makeText(baseContext, "App >>>> Terminate", Toast.LENGTH_SHORT).show()
    }
}
