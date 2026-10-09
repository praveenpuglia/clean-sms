package com.praveenpuglia.cleansms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide contact matching for SMS addresses: a bulk index of the phone table plus a
 * cache of resolved hits. Both are dropped whenever the contacts provider changes.
 * Lookups are blocking and thread-safe; call them off the main thread.
 */
object ContactDirectory {
    private const val TAG = "ContactDirectory"
    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val defaultRegion by lazy { Locale.getDefault().country.ifEmpty { "US" } }

    // Keyed by E.164 or digits-only for phone numbers; raw key for alphanumeric senders.
    private val cache = ConcurrentHashMap<String, ContactInfo>()
    @Volatile private var index: Map<String, ContactInfo>? = null
    @Volatile private var observing = false

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Inbox enrichment: only mobile-like numbers, cache then bulk index (built on first use). */
    fun resolve(context: Context, rawAddress: String): ContactInfo? {
        if (!hasPermission(context) || !isMobileNumberCandidate(rawAddress)) return null
        val idx = ensureIndex(context)
        for (key in candidateKeys(rawAddress)) {
            cache[key]?.let { return it }
            idx?.get(key)?.let { hit ->
                cache[key] = hit
                return hit
            }
        }
        return null
    }

    /** Single-address enrichment (notifications, thread header): cache, index, then PhoneLookup. */
    fun enrich(context: Context, rawAddress: String): ContactInfo? {
        val keys = candidateKeys(rawAddress)
        keys.firstNotNullOfOrNull { cache[it] }?.let { return it }
        index?.let { idx -> keys.firstNotNullOfOrNull { idx[it] }?.let { return it } }
        return quickPhoneLookup(context, rawAddress)
    }

    /** Resolves the contact's lookup URI via PhoneLookup and caches it for [rawAddress]. */
    fun findLookupUri(context: Context, rawAddress: String, name: String?, photoUri: String?): Uri? {
        val keys = candidateKeys(rawAddress)
        for (key in linkedSetOf(rawAddress) + keys) {
            val uri = query(context, ContactsContract.PhoneLookup.CONTENT_FILTER_URI.withPath(key),
                arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup._ID)) { c ->
                val lookupKey = c.stringAt(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val contactId = c.longAt(ContactsContract.PhoneLookup._ID)
                if (!lookupKey.isNullOrEmpty() && contactId != null) ContactsContract.Contacts.getLookupUri(contactId, lookupKey) else null
            } ?: continue
            val info = ContactInfo(name, photoUri, uri.toString())
            if (keys.isEmpty()) cache[rawAddress] = info else keys.forEach { cache[it] = info }
            return uri
        }
        return null
    }

    /**
     * Prioritized keys to try against the index, most specific first:
     * E.164, normalized, digits, then last 10 (+/0 prefixed), 9, 8 and 7 digits.
     */
    fun candidateKeys(rawAddress: String): List<String> {
        val keys = LinkedHashSet<String>()
        e164(rawAddress)?.let { keys += it }
        val normalized = normalize(rawAddress)
        if (normalized.isNotBlank()) keys += normalized
        val digits = normalized.filter(Char::isDigit)
        if (digits.isNotBlank()) keys += digits
        if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            keys += last10
            keys += "+$last10"
            keys += "0$last10"
        }
        if (digits.length >= 9) keys += digits.takeLast(9)
        if (digits.length >= 8) keys += digits.takeLast(8)
        if (digits.length >= 7) keys += digits.takeLast(7)
        return keys.toList()
    }

    /**
     * True for digit-like addresses that look like personal numbers. False for alphanumeric
     * senders, shortcodes (< 7 digits) and parsed non-mobile types under 10 digits.
     */
    fun isMobileNumberCandidate(rawAddress: String): Boolean {
        if (rawAddress.isBlank() || rawAddress.any(Char::isLetter)) return false
        val digits = normalize(rawAddress).filter(Char::isDigit)
        if (digits.length < 7) return false
        return try {
            when (phoneUtil.getNumberType(phoneUtil.parse(rawAddress, defaultRegion))) {
                PhoneNumberUtil.PhoneNumberType.MOBILE,
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE,
                PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER -> true
                else -> digits.length >= 10
            }
        } catch (_: NumberParseException) {
            digits.length >= 10
        }
    }

    @Synchronized
    private fun ensureIndex(context: Context): Map<String, ContactInfo>? {
        index?.let { return it }
        observeChanges(context)
        val built = buildIndex(context)
        Log.d(TAG, "Built contacts index with ${built.size} entries")
        index = built
        return built
    }

    private fun observeChanges(context: Context) {
        if (observing) return
        try {
            context.applicationContext.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = invalidate()
                },
            )
            observing = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Contacts observer unavailable: ${e.javaClass.simpleName}")
        }
    }

    fun invalidate() {
        index = null
        cache.clear()
    }

    private fun buildIndex(context: Context): Map<String, ContactInfo> {
        val map = HashMap<String, ContactInfo>()
        query(
            context,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ),
            single = false,
        ) { c ->
            val number = c.stringAt(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (number.isNullOrEmpty()) return@query null
            val lookupKey = c.stringAt(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val contactId = c.longAt(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupUri = if (!lookupKey.isNullOrEmpty() && contactId != null) {
                ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString()
            } else null
            val info = ContactInfo(
                c.stringAt(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                c.stringAt(ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                lookupUri,
            )
            val key = e164(number) ?: normalize(number).filter(Char::isDigit).ifEmpty { number }
            map[key] = info
            // Also index by digit suffixes for quick suffix matches.
            val digits = number.filter(Char::isDigit)
            if (digits.length >= 7) map[digits.takeLast(7)] = info
            if (digits.length >= 9) map[digits.takeLast(9)] = info
            if (digits.length >= 10) map[digits.takeLast(10)] = info
            null
        }
        return map
    }

    private fun quickPhoneLookup(context: Context, raw: String): ContactInfo? {
        val normalized = normalize(raw)
        val candidates = linkedSetOf(raw)
        if (normalized.isNotBlank()) candidates += normalized
        e164(raw)?.let { candidates += it }
        if (!normalized.startsWith("+")) candidates += "+$normalized"
        val digits = normalized.filter(Char::isDigit)
        if (digits.length >= 10) candidates += digits.takeLast(10)
        if (digits.length >= 7) candidates += digits.takeLast(7)

        for (candidate in candidates) {
            val hit = query(
                context,
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI.withPath(candidate),
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                ),
            ) { c ->
                val contactId = c.longAt(ContactsContract.PhoneLookup._ID)
                val lookupKey = c.stringAt(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val photo = c.stringAt(ContactsContract.PhoneLookup.PHOTO_URI).takeUnless { it.isNullOrEmpty() }
                    ?: contactId?.let { contactPhoto(context, it) }
                ContactInfo(
                    c.stringAt(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    photo,
                    if (!lookupKey.isNullOrEmpty() && contactId != null) {
                        ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString()
                    } else null,
                )
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun contactPhoto(context: Context, contactId: Long): String? = query(
        context,
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()),
        arrayOf(ContactsContract.Contacts.PHOTO_URI),
    ) { it.stringAt(ContactsContract.Contacts.PHOTO_URI) }

    /**
     * Runs a provider query and maps the first row (or, with [single] = false, visits every row
     * and returns the first non-null result). Provider failures are treated as "no match".
     */
    private fun <T> query(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        single: Boolean = true,
        map: (android.database.Cursor) -> T?,
    ): T? = try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            var result: T? = null
            while (result == null && c.moveToNext()) {
                result = map(c)
                if (single) break
            }
            result
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Contacts query failed: ${e.javaClass.simpleName}")
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Contacts query failed: ${e.javaClass.simpleName}")
        null
    } catch (e: SQLiteException) {
        Log.w(TAG, "Contacts query failed: ${e.javaClass.simpleName}")
        null
    }

    private fun e164(raw: String): String? = try {
        phoneUtil.format(phoneUtil.parse(raw, defaultRegion), PhoneNumberUtil.PhoneNumberFormat.E164).takeIf(String::isNotBlank)
    } catch (_: NumberParseException) {
        null
    }

    private fun normalize(raw: String) = PhoneNumberUtils.normalizeNumber(raw).ifEmpty { raw.replace(Regex("\\s+"), "") }

    private fun Uri.withPath(segment: String): Uri = Uri.withAppendedPath(this, Uri.encode(segment))
    private fun android.database.Cursor.stringAt(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)
    private fun android.database.Cursor.longAt(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong)
}
