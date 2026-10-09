package com.praveenpuglia.cleansms

import android.app.Activity

/**
 * Applies the user's selected font as a theme overlay. Activities must call this BEFORE
 * `super.onCreate` so layout inflation uses the right font.
 */
object FontThemeHelper {
    fun apply(activity: Activity) {
        val overlay = when (AppSettings.getFontFamily(activity)) {
            AppSettings.FontFamily.SANS_SERIF -> R.style.ThemeOverlay_CleanSMS_Font_SansSerif
            AppSettings.FontFamily.MONOSPACE -> R.style.ThemeOverlay_CleanSMS_Font_Monospace
            AppSettings.FontFamily.SYSTEM -> R.style.ThemeOverlay_CleanSMS_Font_System
        }
        activity.theme.applyStyle(overlay, true)
    }
}
