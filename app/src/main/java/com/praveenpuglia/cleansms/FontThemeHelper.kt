package com.praveenpuglia.cleansms

import android.app.Activity

/**
 * Applies the user's selected font as a theme overlay. Activities must call this BEFORE
 * `super.onCreate` so layout inflation uses the right font.
 */
object FontThemeHelper {
    fun apply(activity: Activity) {
        val overlay = when (SettingsActivity.getFontFamily(activity)) {
            SettingsActivity.FontFamily.SANS_SERIF -> R.style.ThemeOverlay_CleanSMS_Font_SansSerif
            SettingsActivity.FontFamily.MONOSPACE -> R.style.ThemeOverlay_CleanSMS_Font_Monospace
            SettingsActivity.FontFamily.SYSTEM -> R.style.ThemeOverlay_CleanSMS_Font_System
        }
        activity.theme.applyStyle(overlay, true)
    }
}
