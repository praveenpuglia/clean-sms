package com.praveenpuglia.cleansms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class SettingsActivity : AppCompatActivity() {

    enum class DefaultTab {
        OTP,
        PERSONAL,
        TRANSACTIONAL,
        SERVICE,
        PROMOTIONAL,
        GOVERNMENT
    }

    companion object {
        private const val PREFS_NAME = "CleanSmsPrefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DEFAULT_TAB = "default_tab"
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

        fun getDefaultTab(context: Context): DefaultTab {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Handle migration from old string-based format to new enum ordinal format
            if (prefs.contains(KEY_DEFAULT_TAB)) {
                try {
                    // Try to read as int (new format)
                    val ordinal = prefs.getInt(KEY_DEFAULT_TAB, -1)
                    if (ordinal >= 0) {
                        return DefaultTab.values().getOrNull(ordinal) ?: DefaultTab.OTP
                    }
                } catch (e: ClassCastException) {
                    // Old format - migrate from string to enum
                    val oldValue = prefs.getString(KEY_DEFAULT_TAB, null)
                    val migratedTab = when (oldValue) {
                        "OTP", "OTPs" -> DefaultTab.OTP
                        "Personal" -> DefaultTab.PERSONAL
                        "Transactions" -> DefaultTab.TRANSACTIONAL
                        "Service", "Services" -> DefaultTab.SERVICE
                        "Promotions" -> DefaultTab.PROMOTIONAL
                        "Government", "Governmental" -> DefaultTab.GOVERNMENT
                        else -> DefaultTab.OTP
                    }
                    // Save in new format
                    setDefaultTab(context, migratedTab)
                    return migratedTab
                }
            }
            
            return DefaultTab.OTP
        }

        fun setDefaultTab(context: Context, tab: DefaultTab) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_DEFAULT_TAB, tab.ordinal).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupHeader()
        setupThemeToggle()
        setupDefaultTabDropdown()
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

    private fun setupDefaultTabDropdown() {
        val button = findViewById<MaterialButton>(R.id.default_tab_button)
        
        // Set current selection
        val currentTab = getDefaultTab(this)
        button.text = getLabelForDefaultTab(currentTab)
        
        // Setup popup menu
        button.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_default_tab, popup.menu)
            
            // Set current item checked
            val currentItemId = when (currentTab) {
                DefaultTab.OTP -> R.id.tab_otps
                DefaultTab.PERSONAL -> R.id.tab_personal
                DefaultTab.TRANSACTIONAL -> R.id.tab_transactions
                DefaultTab.SERVICE -> R.id.tab_services
                DefaultTab.PROMOTIONAL -> R.id.tab_promotions
                DefaultTab.GOVERNMENT -> R.id.tab_governmental
            }
            popup.menu.findItem(currentItemId)?.isChecked = true
            
            popup.setOnMenuItemClickListener { item: MenuItem ->
                val selectedTab = when (item.itemId) {
                    R.id.tab_otps -> DefaultTab.OTP
                    R.id.tab_personal -> DefaultTab.PERSONAL
                    R.id.tab_transactions -> DefaultTab.TRANSACTIONAL
                    R.id.tab_services -> DefaultTab.SERVICE
                    R.id.tab_promotions -> DefaultTab.PROMOTIONAL
                    R.id.tab_governmental -> DefaultTab.GOVERNMENT
                    else -> DefaultTab.OTP
                }
                button.text = getLabelForDefaultTab(selectedTab)
                setDefaultTab(this, selectedTab)
                true
            }
            
            popup.show()
        }
    }
    
    private fun getLabelForDefaultTab(tab: DefaultTab): String = when (tab) {
        DefaultTab.OTP -> getString(R.string.tab_otp)
        DefaultTab.PERSONAL -> getString(R.string.category_personal)
        DefaultTab.TRANSACTIONAL -> getString(R.string.category_transactions)
        DefaultTab.SERVICE -> getString(R.string.category_service)
        DefaultTab.PROMOTIONAL -> getString(R.string.category_promotions)
        DefaultTab.GOVERNMENT -> getString(R.string.category_government)
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
            openUrl("https://clean-sms.praveenpuglia.com/tnc")
        }

        // Privacy Policy click listener
        findViewById<TextView>(R.id.privacy_policy).setOnClickListener {
            openUrl("https://clean-sms.praveenpuglia.com/privacy-policy")
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
