package com.praveenpuglia.cleansms

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Material You dynamic colors based on system wallpaper
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
