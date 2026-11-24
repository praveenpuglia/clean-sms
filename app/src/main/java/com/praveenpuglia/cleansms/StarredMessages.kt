package com.praveenpuglia.cleansms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit

/**
 * Starred SMS, by provider message id. The SMS provider has no starred column, so the ids live in
 * app storage on the device. Deleting a message here drops its id; [prune] catches the rest
 * (whole threads deleted from the inbox, or messages deleted by another app).
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

    fun remove(context: Context, messageIds: Collection<Long>) {
        if (messageIds.isEmpty()) return
        val ids = ids(context) - messageIds.toSet()
        prefs(context).edit { putStringSet(KEY_IDS, ids.mapTo(HashSet(), Long::toString)) }
    }

    /** Drops ids whose messages no longer exist. Blocking: call off the main thread. */
    fun prune(context: Context) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val existing = try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms._ID} IN (${ids.joinToString(",")})",
                null,
                null,
            )?.use { cursor -> buildSet<Long> { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
        } catch (error: SecurityException) {
            Log.w(TAG, "Can't check starred messages: ${error.javaClass.simpleName}")
            null
        } ?: return // couldn't look, so keep every star rather than wipe them
        remove(context, ids - existing)
    }

    private const val TAG = "StarredMessages"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
