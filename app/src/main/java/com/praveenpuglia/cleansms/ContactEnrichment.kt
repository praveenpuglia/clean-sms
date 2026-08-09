package com.praveenpuglia.cleansms

import android.app.role.RoleManager
import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object DefaultSmsHelper {
    fun isDefaultSmsApp(context: Context): Boolean {
        try {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                return true
            }
        } catch (_: Exception) {
        }
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

object ContactEnrichment {
    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val defaultRegion by lazy { Locale.getDefault().country.ifEmpty { "US" } }

    fun enrich(context: Context, rawAddress: String): ContactInfo? {
        MainActivity.lookupFromCache(rawAddress)?.let { return it }
        MainActivity.lookupFromIndex(rawAddress)?.let { return it }
        return quickPhoneLookup(context, rawAddress)
    }

    private fun quickPhoneLookup(context: Context, raw: String): ContactInfo? {
        val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(raw).ifEmpty { raw.replace(Regex("\\s+"), "") }
        val candidates = linkedSetOf(raw)
        if (normalized.isNotBlank()) candidates += normalized
        try {
            val parsed = phoneUtil.parse(raw, defaultRegion)
            phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164).takeIf(String::isNotBlank)?.let { candidates += it }
        } catch (_: Exception) {
        }
        if (!normalized.startsWith("+")) candidates += "+$normalized"
        val digits = normalized.filter(Char::isDigit)
        if (digits.length >= 10) candidates += digits.takeLast(10)
        if (digits.length >= 7) candidates += digits.takeLast(7)

        for (candidate in candidates) {
            try {
                val lookupUri = android.net.Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(candidate),
                )
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                )
                context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    val lookupIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    var photo = if (photoIndex >= 0) cursor.getString(photoIndex) else null
                    val contactId = if (idIndex >= 0) cursor.getLong(idIndex) else null
                    val lookupKey = if (lookupIndex >= 0) cursor.getString(lookupIndex) else null
                    if (photo.isNullOrEmpty() && contactId != null) {
                        val contactUri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                        context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.PHOTO_URI), null, null, null)?.use { contact ->
                            if (contact.moveToFirst()) {
                                val index = contact.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                if (index >= 0) photo = contact.getString(index)
                            }
                        }
                    }
                    val contactLookup = if (!lookupKey.isNullOrEmpty() && contactId != null) {
                        ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString()
                    } else {
                        null
                    }
                    return ContactInfo(name, photo, contactLookup)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
