package am.ggtaxi.main.ggdriver.main.app

import com.aram_dev.mapbox.BuildConfig
import timber.log.Timber

fun initTimber() {
    if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
}