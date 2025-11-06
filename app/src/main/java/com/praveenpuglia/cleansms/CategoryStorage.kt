package com.praveenpuglia.cleansms

import android.content.Context
import android.content.SharedPreferences

object CategoryStorage {
    private const val PREF_NAME = "thread_categories"
    private const val KEY_PREFIX = "thread_"
    private const val KEY_VERSION = "categorization_version"
    private const val CURRENT_VERSION = 4 // v4: TRAI header regex now 1–6 chars + mandatory suffix for classification (fix JX-BOLT-S) (§3, §6, §10)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check version and clear cache if outdated
     */
    private fun checkVersion(context: Context) {
        val prefs = getPrefs(context)
        val savedVersion = prefs.getInt(KEY_VERSION, 0)
        if (savedVersion < CURRENT_VERSION) {
            // Clear all cached categories
            prefs.edit().clear().putInt(KEY_VERSION, CURRENT_VERSION).apply()
        }
    }

    /**
     * Save category for a thread (by address since thread IDs can change)
     */
    fun saveCategory(context: Context, address: String, category: MessageCategory) {
        getPrefs(context).edit()
            .putString("$KEY_PREFIX$address", category.name)
            .apply()
    }

    /**
     * Get saved category for a thread
     */
    fun getCategory(context: Context, address: String): MessageCategory? {
        val categoryName = getPrefs(context).getString("$KEY_PREFIX$address", null)
        return categoryName?.let {
            try {
                MessageCategory.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Get or compute category for an address/thread
     */
    fun getCategoryOrCompute(context: Context, address: String, threadId: Long): MessageCategory {
        // Check version and clear cache if needed
        checkVersion(context)
        
        // Check if we have a saved category
        val saved = getCategory(context, address)
        if (saved != null) return saved

        // Try TRAI/phone detection first
        var category = CategoryClassifier.categorizeAddress(address)

        // If unknown, infer from content
        if (category == MessageCategory.UNKNOWN) {
            category = CategoryClassifier.inferCategoryFromContent(context, address, threadId)
            // Save the inferred category
            saveCategory(context, address, category)
        } else {
            // Save the detected category
            saveCategory(context, address, category)
        }

        return category
    }
}
