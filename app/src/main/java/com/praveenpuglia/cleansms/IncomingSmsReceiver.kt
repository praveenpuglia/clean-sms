package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.graphics.Bitmap
import android.net.Uri
import java.io.InputStream
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

class IncomingSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Ensure we are default SMS app; if not, ignore.
        if (!DefaultSmsHelper.isDefaultSmsApp(context)) {
            Log.d("IncomingSms", "Not default SMS app; ignoring incoming broadcast")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        // Combine multipart message bodies
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
        val originatingAddress = messages.first().originatingAddress ?: "Unknown"

        val enriched = ContactEnrichment.enrich(context, originatingAddress)
        val displayName = enriched?.first
        val photoUri = enriched?.second

        showNotification(context, originatingAddress, displayName, photoUri, fullBody)

        // Notify activity to refresh threads list
        MainActivity.refreshThreadsIfActive()
    }

    private fun showNotification(
        context: Context,
        address: String,
        name: String?,
        photoUri: String?,
        body: String
    ) {
        val channelId = "incoming_sms"
        ensureChannel(context, channelId)

        val title = name ?: address
        val content = body.take(120)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)

        val largeIcon = loadBitmap(context, photoUri)
        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        NotificationManagerCompat.from(context).notify(address.hashCode(), builder.build())
    }

    private fun ensureChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(channelId)
            if (existing == null) {
                val ch = NotificationChannel(channelId, "Incoming SMS", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "Notifications for received SMS messages"
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun loadBitmap(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            stream.use { s -> if (s != null) android.graphics.BitmapFactory.decodeStream(s) else null }
        } catch (e: Exception) {
            null
        }
    }
}

/** Helper for default SMS app checks */
object DefaultSmsHelper {
    fun isDefaultSmsApp(context: Context): Boolean {
        // Prefer RoleManager on modern Android versions
        try {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) return true
            }
        } catch (_: Exception) {}
        val pkg = Telephony.Sms.getDefaultSmsPackage(context)
        return pkg == context.packageName
    }
}

/** Simple shared enrichment logic reused by receiver without rebuilding full index each time */
object ContactEnrichment {
    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val defaultRegion: String by lazy { Locale.getDefault().country.ifEmpty { "US" } }

    fun enrich(context: Context, rawAddress: String): Pair<String?, String?>? {
        if (!MainActivity.isMobileNumberCandidateStatic(rawAddress)) return null
        // Try cache and index from MainActivity if available
        val fromCache = MainActivity.lookupFromCache(rawAddress)
        if (fromCache != null) return fromCache
        val fromIndex = MainActivity.lookupFromIndex(rawAddress)
        if (fromIndex != null) return fromIndex
        // Fallback: lightweight parse attempt + PhoneLookup only (no deep scans)
        return quickPhoneLookup(context, rawAddress)
    }

    private fun quickPhoneLookup(context: Context, raw: String): Pair<String?, String?>? {
        val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(raw).ifEmpty { raw.replace(Regex("\\s+"), "") }
        val candidates = LinkedHashSet<String>()
        candidates += raw
        if (normalized.isNotBlank()) candidates += normalized
        try {
            val parsed = phoneUtil.parse(raw, defaultRegion)
            val e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            if (e164.isNotBlank()) candidates += e164
        } catch (_: Exception) {}
        if (!normalized.startsWith("+")) candidates += "+" + normalized
        val digits = normalized.filter { it.isDigit() }
        if (digits.length >= 10) candidates += digits.takeLast(10)
        if (digits.length >= 7) candidates += digits.takeLast(7)

        for (c in candidates) {
            try {
                val lookupUri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(c))
                val proj = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME, android.provider.ContactsContract.PhoneLookup.PHOTO_URI)
                context.contentResolver.query(lookupUri, proj, null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) {
                        val idxName = cur.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val idxPhoto = cur.getColumnIndex(android.provider.ContactsContract.PhoneLookup.PHOTO_URI)
                        val name = if (idxName >= 0) cur.getString(idxName) else null
                        val photo = if (idxPhoto >= 0) cur.getString(idxPhoto) else null
                        return Pair(name, photo)
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
