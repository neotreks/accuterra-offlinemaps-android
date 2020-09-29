package com.neotreks.accuterra.accuterraofflinemaps

import android.app.Application
import com.mapbox.mapboxsdk.Mapbox

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val mapboxToken = BuildConfig.MAPBOX_TOKEN
        require(!mapboxToken.isNullOrBlank()) { "MAPBOX_TOKEN not set in build.gradle" }
        Mapbox.getInstance(this, mapboxToken)
    }
}