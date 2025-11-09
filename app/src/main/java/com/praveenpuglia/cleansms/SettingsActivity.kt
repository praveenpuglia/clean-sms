package com.praveenpuglia.cleansms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButtonToggleGroup

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "CleanSmsPrefs"
        private const val KEY_THEME = "theme_mode"
        const val THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
        const val THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES
        const val THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

        fun getThemeMode(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_THEME, THEME_SYSTEM)
        }

        fun setThemeMode(context: Context, mode: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_THEME, mode).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupHeader()
        setupThemeToggle()
        setupAboutSection()
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }
    }

    private fun setupThemeToggle() {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle_group)
        
        // Set current selection based on saved preference
        val currentTheme = getThemeMode(this)
        when (currentTheme) {
            THEME_LIGHT -> toggleGroup.check(R.id.theme_light)
            THEME_DARK -> toggleGroup.check(R.id.theme_dark)
            THEME_SYSTEM -> toggleGroup.check(R.id.theme_system)
        }

        // Listen for theme changes
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newTheme = when (checkedId) {
                    R.id.theme_light -> THEME_LIGHT
                    R.id.theme_dark -> THEME_DARK
                    R.id.theme_system -> THEME_SYSTEM
                    else -> THEME_SYSTEM
                }
                
                // Save preference
                setThemeMode(this, newTheme)
                
                // Apply theme immediately
                AppCompatDelegate.setDefaultNightMode(newTheme)
            }
        }
    }

    private fun setupAboutSection() {
        // Set app version
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.app_version).text = versionName
        } catch (e: Exception) {
            findViewById<TextView>(R.id.app_version).text = "Unknown"
        }

        // Terms & Conditions click listener
        findViewById<TextView>(R.id.terms_conditions).setOnClickListener {
            openUrl("https://praveenpuglia.com")
        }

        // Privacy Policy click listener
        findViewById<TextView>(R.id.privacy_policy).setOnClickListener {
            openUrl("https://praveenpuglia.com")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Handle case where no browser is available
        }
    }
}
