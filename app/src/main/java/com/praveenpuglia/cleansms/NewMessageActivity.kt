package com.praveenpuglia.cleansms

import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionInfo
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        availableSims = SimSlots.activeSims(this)
        selectedSimIndex = SimSlots.defaultIndex(availableSims)
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

        // ponytail: synchronous on purpose — finish() follows immediately, and an async send could
        // outlive a rotation and leave the text in place for an accidental duplicate send.
        try {
            val subscriptionId = availableSims.getOrNull(selectedSimIndex)?.subscriptionId
            selectedRecipients.forEach { recipient ->
                SmsSender.send(this, recipient.phoneNumber, body, subscriptionId)
            }

            val sim = availableSims.getOrNull(selectedSimIndex)
                ?.takeIf { availableSims.size > 1 }
                ?.let { " via SIM ${it.simSlotIndex + 1}" }
                .orEmpty()
            val message = if (selectedRecipients.size == 1) "Message sent$sim" else "Messages sent$sim"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
