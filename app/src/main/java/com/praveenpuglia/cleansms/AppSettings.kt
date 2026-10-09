package com.praveenpuglia.cleansms

import android.content.Context
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatDelegate

/** User preferences stored in the shared "CleanSmsPrefs" file. Keys are stable across releases. */
object AppSettings {
    enum class DefaultTab { OTP, PERSONAL, TRANSACTIONAL, SERVICE, PROMOTIONAL, GOVERNMENT, ALL }

    enum class FontFamily { SANS_SERIF, MONOSPACE, SYSTEM }

    private const val PREFS_NAME = "CleanSmsPrefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_DEFAULT_TAB = "default_tab"
    private const val KEY_PROMO_NOTIFICATIONS_ENABLED = "promo_notifications_enabled"
    private const val KEY_ALL_TAB_ENABLED = "all_tab_enabled"
    private const val KEY_FONT_FAMILY = "font_family"
    const val THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
    const val THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES
    const val THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    fun getThemeMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_SYSTEM)
    }

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_THEME, mode) }
    }

    fun getDefaultTab(context: Context): DefaultTab {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DEFAULT_TAB)) {
            try {
                val ordinal = prefs.getInt(KEY_DEFAULT_TAB, -1)
                if (ordinal >= 0) return DefaultTab.entries.getOrNull(ordinal) ?: DefaultTab.OTP
            } catch (_: ClassCastException) {
                val migratedTab = when (prefs.getString(KEY_DEFAULT_TAB, null)) {
                    "OTP", "OTPs" -> DefaultTab.OTP
                    "Personal" -> DefaultTab.PERSONAL
                    "Transactions" -> DefaultTab.TRANSACTIONAL
                    "Service", "Services" -> DefaultTab.SERVICE
                    "Promotions" -> DefaultTab.PROMOTIONAL
                    "Government", "Governmental" -> DefaultTab.GOVERNMENT
                    "All" -> DefaultTab.ALL
                    else -> DefaultTab.OTP
                }
                setDefaultTab(context, migratedTab)
                return migratedTab
            }
        }
        return DefaultTab.OTP
    }

    fun setDefaultTab(context: Context, tab: DefaultTab) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_DEFAULT_TAB, tab.ordinal) }
    }

    fun getPromoNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMO_NOTIFICATIONS_ENABLED, true)
    }

    fun setPromoNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PROMO_NOTIFICATIONS_ENABLED, enabled) }
    }

    fun getAllTabEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALL_TAB_ENABLED, false)
    }

    fun setAllTabEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ALL_TAB_ENABLED, enabled) }
    }

    fun getFontFamily(context: Context): FontFamily {
        val ordinal = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FONT_FAMILY, FontFamily.SANS_SERIF.ordinal)
        return FontFamily.entries.getOrNull(ordinal) ?: FontFamily.SANS_SERIF
    }

    fun setFontFamily(context: Context, family: FontFamily) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_FONT_FAMILY, family.ordinal) }
    }

    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    fun isOnboardingCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ONBOARDING_COMPLETED, true) }
    }
}
