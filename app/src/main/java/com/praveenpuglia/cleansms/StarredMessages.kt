package com.praveenpuglia.cleansms

import android.content.Context
import androidx.core.content.edit

/**
 * Starred SMS, by provider message id. The SMS provider has no starred column, so the ids live in
 * app storage on the device. ponytail: ids of deleted messages linger (the provider never reuses
 * ids, and a set of longs is tiny); prune them if that ever matters.
 */
object StarredMessages {
    private const val PREFS = "starred_messages"
    private const val KEY_IDS = "ids"

    fun ids(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()

    /** Returns the new set. */
    fun toggle(context: Context, messageId: Long): Set<Long> {
        val ids = ids(context).let { if (messageId in it) it - messageId else it + messageId }
        prefs(context).edit { putStringSet(KEY_IDS, ids.mapTo(HashSet(), Long::toString)) }
        return ids
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
