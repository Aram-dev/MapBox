package com.aram_dev.mapbox.pluginsapp

import androidx.multidex.MultiDexApplication
import com.aram_dev.mapbox.BuildConfig
import com.aram_dev.mapbox.R

import com.mapbox.mapboxsdk.Mapbox

import timber.log.Timber

class PluginApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        initializeLogger()
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
    }

    private fun initializeLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
