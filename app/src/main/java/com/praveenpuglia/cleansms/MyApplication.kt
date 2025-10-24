package com.praveenpuglia.cleansms

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Material You dynamic colors based on system wallpaper
        // This will apply dynamic colors on Android 12+ (API 31+)
        // and fall back to the theme colors on older versions
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
