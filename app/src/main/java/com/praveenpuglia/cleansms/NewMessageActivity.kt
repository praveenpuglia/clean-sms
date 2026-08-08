package com.praveenpuglia.cleansms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageScreen
import com.praveenpuglia.cleansms.ui.newmessage.smsCounter
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme

class NewMessageActivity : AppCompatActivity() {
    private var allContacts: List<ContactSuggestion> = emptyList()
    private val selectedRecipients = mutableStateListOf<ContactSuggestion>()
    private var recipientQuery by mutableStateOf("")
    private var messageText by mutableStateOf("")
    private var availableSims by mutableStateOf<List<SubscriptionInfo>>(emptyList())
    private var selectedSimIndex by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        super.onCreate(savedInstanceState)

        loadContacts()
        setupSimSelector()
        recipientQuery = savedInstanceState?.getString(STATE_RECIPIENT_QUERY).orEmpty()
        messageText = savedInstanceState?.getString(STATE_MESSAGE).orEmpty()
        handleIncomingIntent(restoreBody = savedInstanceState == null)

        setContent {
            val suggestions = filterContacts(recipientQuery)
            CleanSmsTheme {
                NewMessageScreen(
                    recipients = selectedRecipients,
                    recipientQuery = recipientQuery,
                    message = messageText,
                    suggestions = suggestions,
                    counter = smsCounter(messageText),
                    selectedSimNumber = availableSims.getOrNull(selectedSimIndex)?.simSlotIndex?.plus(1),
                    showSimSelector = availableSims.size > 1,
                    onBack = ::finish,
                    onRecipientQueryChange = { recipientQuery = it },
                    onRecipientSelected = ::onContactSelected,
                    onRecipientRemoved = { selectedRecipients.remove(it) },
                    onRemoveLastRecipient = {
                        if (recipientQuery.isEmpty() && selectedRecipients.isNotEmpty()) {
                            selectedRecipients.removeAt(selectedRecipients.lastIndex)
                            true
                        } else {
                            false
                        }
                    },
                    onMessageChange = { messageText = it },
                    onSimToggle = {
                        selectedSimIndex = (selectedSimIndex + 1) % availableSims.size
                    },
                    onSend = ::sendMessage,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_RECIPIENT_QUERY, recipientQuery)
        outState.putString(STATE_MESSAGE, messageText)
        super.onSaveInstanceState(outState)
    }

    private fun handleIncomingIntent(restoreBody: Boolean) {
        intent?.data?.let { uri ->
            if (uri.scheme?.startsWith("sms") != true && uri.scheme?.startsWith("mms") != true) return

            val schemeSpecificPart = uri.schemeSpecificPart
            val phoneNumber = schemeSpecificPart.substringBefore('?').trim()
            val body = schemeSpecificPart.substringAfter('?', "")
                .takeIf(String::isNotEmpty)
                ?.let { parseQueryParameter(it, "body") }

            if (phoneNumber.isNotEmpty()) {
                findContactByPhoneNumber(phoneNumber)?.let { contact ->
                    if (selectedRecipients.none { it.phoneNumber == contact.phoneNumber }) {
                        selectedRecipients.add(contact)
                    }
                }
            }
            if (restoreBody && !body.isNullOrBlank()) messageText = body
        }
    }

    private fun parseQueryParameter(queryString: String, paramName: String): String? {
        queryString.split('&').forEach { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == paramName) return Uri.decode(parts[1])
        }
        return null
    }

    private fun findContactByPhoneNumber(phoneNumber: String): ContactSuggestion? {
        val normalizedInput = normalizeNumber(phoneNumber)
        allContacts.firstOrNull { normalizeNumber(it.phoneNumber) == normalizedInput }?.let { return it }

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            )
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                arrayOf(phoneNumber),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val name = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val number = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photo = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val lookup = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                    return ContactSuggestion(
                        contactId = if (id >= 0) cursor.getLong(id) else -1L,
                        name = if (name >= 0) cursor.getString(name) ?: phoneNumber else phoneNumber,
                        phoneNumber = if (number >= 0) cursor.getString(number) ?: phoneNumber else phoneNumber,
                        photoUri = if (photo >= 0) cursor.getString(photo) else null,
                        lookupKey = if (lookup >= 0) cursor.getString(lookup) else null,
                    )
                }
            }
        } catch (_: SecurityException) {
            // A raw number is still usable when contacts permission is unavailable.
        }

        return ContactSuggestion(-1L, phoneNumber, phoneNumber, null, null, isRawNumber = true)
    }

    private fun setupSimSelector() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return

        try {
            val manager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            availableSims = manager?.activeSubscriptionInfoList.orEmpty()
            if (availableSims.size > 1) {
                val defaultId = SubscriptionManager.getDefaultSmsSubscriptionId()
                selectedSimIndex = availableSims.indexOfFirst { it.subscriptionId == defaultId }.coerceAtLeast(0)
            }
        } catch (_: SecurityException) {
            availableSims = emptyList()
        }
    }

    private fun loadContacts() {
        val contacts = mutableListOf<ContactSuggestion>()
        val seen = HashSet<String>()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            )
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )?.use { cursor ->
                val id = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val name = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val number = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photo = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val lookup = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (cursor.moveToNext()) {
                    val contactName = if (name >= 0) cursor.getString(name) else null
                    val rawNumber = if (number >= 0) cursor.getString(number) else null
                    if (contactName != null && rawNumber != null) {
                        val contactId = if (id >= 0) cursor.getLong(id) else 0L
                        if (seen.add("$contactId:${normalizeNumber(rawNumber)}")) {
                            contacts += ContactSuggestion(
                                contactId = contactId,
                                name = contactName,
                                phoneNumber = rawNumber.trim(),
                                photoUri = if (photo >= 0) cursor.getString(photo) else null,
                                lookupKey = if (lookup >= 0) cursor.getString(lookup) else null,
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Raw-number entry remains available without contacts permission.
        }
        allContacts = contacts
    }

    private fun filterContacts(query: String): List<ContactSuggestion> {
        if (query.isBlank()) return emptyList()

        val filtered = allContacts.filter { contact ->
            (contact.name.contains(query, ignoreCase = true) || contact.phoneNumber.contains(query)) &&
                selectedRecipients.none { it.phoneNumber == contact.phoneNumber }
        }.toMutableList()
        if (isPotentialPhoneNumber(query) && filtered.none { it.phoneNumber == query }) {
            filtered += ContactSuggestion(-1L, query, query, null, null, isRawNumber = true)
        }
        return filtered
    }

    private fun onContactSelected(contact: ContactSuggestion) {
        if (selectedRecipients.none { it.phoneNumber == contact.phoneNumber }) selectedRecipients.add(contact)
        recipientQuery = ""
    }

    private fun sendMessage() {
        if (selectedRecipients.isEmpty()) return
        val body = messageText.trim()
        if (body.isBlank()) return

        try {
            val smsManager = availableSims.getOrNull(selectedSimIndex)?.let {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(it.subscriptionId)
            } ?: run {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            selectedRecipients.forEach { recipient ->
                val parts = smsManager.divideMessage(body)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(recipient.phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(recipient.phoneNumber, null, body, null, null)
                }

                try {
                    val values = ContentValues().apply {
                        put("address", recipient.phoneNumber)
                        put("body", body)
                        put("date", System.currentTimeMillis())
                        put("read", 1)
                        put("type", 2)
                        put("thread_id", Telephony.Threads.getOrCreateThreadId(this@NewMessageActivity, setOf(recipient.phoneNumber)))
                        availableSims.getOrNull(selectedSimIndex)?.let { put("sub_id", it.subscriptionId) }
                    }
                    contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                } catch (_: SecurityException) {
                    // Sending can still succeed when provider insertion is denied.
                }
            }

            val sim = availableSims.getOrNull(selectedSimIndex)
                ?.takeIf { availableSims.size > 1 }
                ?.let { " via SIM ${it.simSlotIndex + 1}" }
                .orEmpty()
            val message = if (selectedRecipients.size == 1) "Message sent$sim" else "Messages sent$sim"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            MainActivity.refreshThreadsIfActive()
            finish()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
        } catch (_: IllegalArgumentException) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeNumber(number: String) = PhoneNumberUtils.normalizeNumber(number).filter(Char::isDigit)

    private fun isPotentialPhoneNumber(input: String): Boolean {
        if (input.isBlank()) return false
        val digits = input.filter(Char::isDigit)
        return digits.length >= 5 && digits.toSet().size > 1 && input.matches(Regex("^[+()0-9 -]{5,}"))
    }

    private companion object {
        const val STATE_RECIPIENT_QUERY = "recipient_query"
        const val STATE_MESSAGE = "message"
    }
}
