package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.graphics.Bitmap
import android.net.Uri
import android.app.PendingIntent
import android.os.Build
import java.io.InputStream
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import android.provider.ContactsContract

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
    val displayName = enriched?.name
    val photoUri = enriched?.photoUri

        val threadId = findOrCreateThreadId(context, originatingAddress)
        val category = CategoryStorage.getCategoryOrCompute(context, originatingAddress, threadId)

        showNotification(context, threadId, originatingAddress, displayName, photoUri, fullBody, category)

        // Notify activity to refresh threads list
        MainActivity.refreshThreadsIfActive()
    }

    private fun showNotification(
        context: Context,
        threadId: Long,
        address: String,
        name: String?,
        photoUri: String?,
        body: String,
        category: MessageCategory
    ) {
        val channelId = "incoming_sms"
        ensureChannel(context, channelId)

        val title = name ?: address
        val content = body.take(120)

        val contentIntent = createThreadDetailPendingIntent(
            context = context,
            threadId = threadId,
            address = address,
            displayName = name,
            photoUri = photoUri,
            category = category
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        val largeIcon = loadBitmap(context, photoUri)
        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        NotificationManagerCompat.from(context).notify(address.hashCode(), builder.build())
    }

    private fun createThreadDetailPendingIntent(
        context: Context,
        threadId: Long,
        address: String,
        displayName: String?,
        photoUri: String?,
        category: MessageCategory
    ): PendingIntent {
        val detailIntent = Intent(context, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", threadId)
            putExtra("CONTACT_NAME", displayName)
            putExtra("CONTACT_ADDRESS", address)
            putExtra("CONTACT_PHOTO_URI", photoUri)
            putExtra("CATEGORY", category.name)
        }

        val mainIntent = Intent(context, MainActivity::class.java)

        val stackBuilder = TaskStackBuilder.create(context).apply {
            addNextIntent(mainIntent)
            addNextIntent(detailIntent)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        val requestCode = (threadId xor address.hashCode().toLong()).toInt()
        return stackBuilder.getPendingIntent(requestCode, flags)
            ?: PendingIntent.getActivity(context, requestCode, detailIntent, flags)
    }

    private fun findOrCreateThreadId(context: Context, address: String): Long {
        val uri = Telephony.Sms.CONTENT_URI
        context.contentResolver.query(
            uri,
            arrayOf("thread_id"),
            "address = ?",
            arrayOf(address),
            "date DESC LIMIT 1"
        )?.use { cursor ->
            val idx = cursor.getColumnIndex("thread_id")
            if (idx >= 0 && cursor.moveToFirst()) {
                val threadId = cursor.getLong(idx)
                if (threadId > 0) return threadId
            }
        }

        return Telephony.Threads.getOrCreateThreadId(context, setOf(address))
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

    fun enrich(context: Context, rawAddress: String): ContactInfo? {
        // Allow enrichment even in cold start: use simplified heuristic
        val candidate = MainActivity.isMobileNumberCandidateStatic(rawAddress)
        // Try cache/index only if activity is active
        val fromCache = MainActivity.lookupFromCache(rawAddress)
        if (fromCache != null) return fromCache
        val fromIndex = MainActivity.lookupFromIndex(rawAddress)
        if (fromIndex != null) return fromIndex
        // Attempt direct PhoneLookup even if heuristic returns false; may still resolve name/photo (e.g., short codes not desired though)
        return if (candidate) quickPhoneLookup(context, rawAddress) else quickPhoneLookup(context, rawAddress)
    }

    private fun quickPhoneLookup(context: Context, raw: String): ContactInfo? {
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
                val lookupUri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(c))
                val proj = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY
                )
                context.contentResolver.query(lookupUri, proj, null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) {
                        val idxName = cur.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val idxPhoto = cur.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                        val idxId = cur.getColumnIndex(ContactsContract.PhoneLookup._ID)
                        val idxLookup = cur.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                        val name = if (idxName >= 0) cur.getString(idxName) else null
                        var photo = if (idxPhoto >= 0) cur.getString(idxPhoto) else null
                        val contactId = if (idxId >= 0) cur.getLong(idxId) else null
                        val lookupKey = if (idxLookup >= 0) cur.getString(idxLookup) else null
                        if (photo.isNullOrEmpty() && contactId != null) {
                            try {
                                val contactUri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                                val p = arrayOf(ContactsContract.Contacts.PHOTO_URI)
                                context.contentResolver.query(contactUri, p, null, null, null)?.use { c2 ->
                                    if (c2.moveToFirst()) {
                                        val idxP = c2.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                        photo = if (idxP >= 0) c2.getString(idxP) else photo
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        val lookupString = if (!lookupKey.isNullOrEmpty() && contactId != null) {
                            ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString()
                        } else {
                            null
                        }
                        return ContactInfo(name, photo, lookupString)
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
