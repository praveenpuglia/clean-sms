package com.praveenpuglia.cleansms

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.provider.ContactsContract
import android.provider.Telephony
import android.content.ContentValues
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NewMessageActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var chipGroup: ChipGroup
    private lateinit var recipientInput: EditText
    private lateinit var contactsList: RecyclerView
    private lateinit var composerContainer: View
    private lateinit var messageInput: EditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var messageCounter: TextView
    
    private lateinit var contactsAdapter: ContactSuggestionAdapter
    private var allContacts: List<ContactSuggestion> = emptyList()
    private val selectedRecipients = mutableListOf<ContactSuggestion>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before setting content view
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)

        backButton = findViewById(R.id.new_message_back_button)
        chipGroup = findViewById(R.id.new_message_recipients_chip_group)
        recipientInput = findViewById(R.id.new_message_recipient_input)
        contactsList = findViewById(R.id.new_message_contacts_list)
        composerContainer = findViewById(R.id.new_message_composer_include)
        messageInput = findViewById(R.id.composer_message_input)
        sendButton = findViewById(R.id.composer_send_button)
        messageCounter = findViewById(R.id.composer_message_counter)

        setupViews()
        loadContacts()
        handleIncomingIntent()
    }

    private fun handleIncomingIntent() {
        // Handle SENDTO intent from contacts app or other apps (e.g., sms:+1234567890?body=message)
        intent?.data?.let { uri ->
            if (uri.scheme?.startsWith("sms") == true || uri.scheme?.startsWith("mms") == true) {
                // Extract phone number and optional body from URI
                // SMS URIs are non-hierarchical, so we parse manually
                val schemeSpecificPart = uri.schemeSpecificPart
                val phoneNumber = schemeSpecificPart.substringBefore('?').trim()
                
                // Extract optional message body from query string (e.g., ?body=Hello&other=param)
                val bodyText = if (schemeSpecificPart.contains('?')) {
                    val queryString = schemeSpecificPart.substringAfter('?')
                    parseQueryParameter(queryString, "body")
                } else {
                    null
                }

                if (phoneNumber.isNotEmpty()) {
                    // Look up contact from phone number or create raw number entry
                    val contact = findContactByPhoneNumber(phoneNumber)
                    
                    // Add contact to recipients
                    if (contact != null && selectedRecipients.none { it.phoneNumber == contact.phoneNumber }) {
                        selectedRecipients.add(contact)
                        addRecipientChip(contact)
                        composerContainer.visibility = View.VISIBLE
                    }

                    // Pre-fill message body if provided
                    if (!bodyText.isNullOrBlank()) {
                        messageInput.setText(bodyText)
                        messageInput.setSelection(bodyText.length) // Cursor at end
                    }

                    // Focus on message input instead of recipient input
                    messageInput.post {
                        messageInput.requestFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }
    }

    private fun parseQueryParameter(queryString: String, paramName: String): String? {
        // Parse query string manually for non-hierarchical URIs
        queryString.split('&').forEach { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == paramName) {
                return android.net.Uri.decode(parts[1])
            }
        }
        return null
    }

    private fun findContactByPhoneNumber(phoneNumber: String): ContactSuggestion? {
        val normalizedInput = normalizeNumber(phoneNumber)
        
        // First, try to find exact match in loaded contacts
        allContacts.firstOrNull { normalizeNumber(it.phoneNumber) == normalizedInput }?.let {
            return it
        }

        // If not found in memory, query contacts database
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
            )

            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                arrayOf(phoneNumber),
                null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idxId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val idxName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val idxNumber = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val idxPhoto = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    val idxLookup = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                    return ContactSuggestion(
                        contactId = if (idxId >= 0) c.getLong(idxId) else -1L,
                        name = if (idxName >= 0) c.getString(idxName) ?: phoneNumber else phoneNumber,
                        phoneNumber = if (idxNumber >= 0) c.getString(idxNumber) ?: phoneNumber else phoneNumber,
                        photoUri = if (idxPhoto >= 0) c.getString(idxPhoto) else null,
                        lookupKey = if (idxLookup >= 0) c.getString(idxLookup) else null,
                        isRawNumber = false
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If still not found, create a raw number contact suggestion
        return ContactSuggestion(
            contactId = -1L,
            name = phoneNumber,
            phoneNumber = phoneNumber,
            photoUri = null,
            lookupKey = null,
            isRawNumber = true
        )
    }

    private fun setupViews() {
        backButton.setOnClickListener { finish() }

        // Autofocus recipient field and show keyboard (unless intent provides recipient)
        val hasIncomingRecipient = intent?.data?.let { uri ->
            (uri.scheme?.startsWith("sms") == true || uri.scheme?.startsWith("mms") == true) &&
            uri.schemeSpecificPart.substringBefore('?').trim().isNotEmpty()
        } ?: false

        if (!hasIncomingRecipient) {
            recipientInput.post {
                recipientInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(recipientInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // Setup contacts list
        contactsAdapter = ContactSuggestionAdapter(emptyList()) { contact ->
            onContactSelected(contact)
        }
        contactsList.layoutManager = LinearLayoutManager(this)
        contactsList.adapter = contactsAdapter

        // Setup recipient input with text watcher
        recipientInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterContacts(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Backspace to remove last chip when input empty
        recipientInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_DEL && event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (recipientInput.text.isNullOrEmpty() && selectedRecipients.isNotEmpty()) {
                    val last = selectedRecipients.removeLast()
                    for (i in chipGroup.childCount - 1 downTo 0) {
                        val v = chipGroup.getChildAt(i)
                        if (v is Chip && v.text.toString() == (if (last.isRawNumber) last.phoneNumber else last.name)) {
                            chipGroup.removeViewAt(i)
                            break
                        }
                    }
                    if (selectedRecipients.isEmpty()) {
                        composerContainer.visibility = View.GONE
                        sendButton.isEnabled = false
                    }
                    return@setOnKeyListener true
                }
            }
            false
        }

        // Setup message input enabling logic and counter
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sendButton.isEnabled = !s.isNullOrBlank() && selectedRecipients.isNotEmpty()
                messageCounter.text = s?.length?.toString() ?: "0"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup send button
        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun loadContacts() {
        // Load all contacts with phone numbers, deduplicating identical numbers per contact
        val contacts = mutableListOf<ContactSuggestion>()
        val seen = HashSet<String>() // normalized key to avoid duplicates

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )

            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use { c ->
                val idxId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val idxName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val idxNumber = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxPhoto = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val idxLookup = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (c.moveToNext()) {
                    val contactId = if (idxId >= 0) c.getLong(idxId) else 0L
                    val name = if (idxName >= 0) c.getString(idxName) else null
                    val numberRaw = if (idxNumber >= 0) c.getString(idxNumber) else null
                    val photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                    val lookup = if (idxLookup >= 0) c.getString(idxLookup) else null

                    if (name != null && numberRaw != null) {
                        val normalized = normalizeNumber(numberRaw)
                        // key uses contact + normalized number so same number across multi-account duplicates is collapsed
                        val key = "$contactId:$normalized"
                        if (seen.add(key)) {
                            contacts.add(
                                ContactSuggestion(
                                    contactId = contactId,
                                    name = name,
                                    phoneNumber = numberRaw.trim(),
                                    photoUri = photo,
                                    lookupKey = lookup
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        allContacts = contacts
    }

    private fun filterContacts(query: String) {
        if (query.isBlank()) {
            contactsList.visibility = View.GONE
            return
        }

        val filtered = allContacts.filter { contact ->
            (contact.name.contains(query, ignoreCase = true) || contact.phoneNumber.contains(query)) &&
            selectedRecipients.none { it.phoneNumber == contact.phoneNumber }
        }

        val mutable = filtered.toMutableList()
        // Add synthetic suggestion if query looks like a phone number and not an exact match already
        if (isPotentialPhoneNumber(query) && filtered.none { it.phoneNumber == query }) {
            mutable.add(
                ContactSuggestion(
                    contactId = -1L,
                    name = query,
                    phoneNumber = query,
                    photoUri = null,
                    lookupKey = null,
                    isRawNumber = true
                )
            )
        }

        if (mutable.isNotEmpty()) {
            contactsAdapter.updateContacts(mutable)
            contactsList.visibility = View.VISIBLE
        } else {
            contactsList.visibility = View.GONE
        }
    }

    private fun onContactSelected(contact: ContactSuggestion) {
        // Avoid duplicates
        if (selectedRecipients.any { it.phoneNumber == contact.phoneNumber }) {
            recipientInput.setText("")
            return
        }
        selectedRecipients.add(contact)
        addRecipientChip(contact)
        recipientInput.setText("")
        contactsList.visibility = View.GONE

        // Show composer when at least one recipient present
        if (selectedRecipients.isNotEmpty()) {
            composerContainer.visibility = View.VISIBLE
            
            // Auto-focus message input for quick typing after selecting first contact
            messageInput.post {
                messageInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // Existing thread lookup removed for simplified flow.

    private fun sendMessage() {
        if (selectedRecipients.isEmpty()) return
        val messageText = messageInput.text.toString().trim()
        if (messageText.isBlank()) return

        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            selectedRecipients.forEach { recipient ->
                smsManager.sendTextMessage(recipient.phoneNumber, null, messageText, null, null)
                // Attempt provider insert for immediate visibility if default SMS app
                try {
                    val threadId = Telephony.Threads.getOrCreateThreadId(this, setOf(recipient.phoneNumber))
                    val values = ContentValues().apply {
                        put("address", recipient.phoneNumber)
                        put("body", messageText)
                        put("date", System.currentTimeMillis())
                        put("read", 1)
                        put("type", 2)
                        put("thread_id", threadId)
                    }
                    contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                } catch (_: SecurityException) { }
            }
            Toast.makeText(this, if (selectedRecipients.size == 1) "Message sent" else "Messages sent", Toast.LENGTH_SHORT).show()
            MainActivity.refreshThreadsIfActive()
            finish() // Return to previous tab (activity stack pop)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun normalizeNumber(number: String): String = PhoneNumberUtils.normalizeNumber(number).filter { it.isDigit() }

    private fun isPotentialPhoneNumber(input: String): Boolean {
        if (input.isBlank()) return false
        val digits = input.filter { it.isDigit() }
        // Basic heuristic: at least 5 digits, not all same char
        if (digits.length < 5) return false
        if (digits.toSet().size == 1) return false
        // Allow +, spaces, dashes, parentheses
        return input.matches(Regex("^[+()0-9 -]{5,}"))
    }

    private fun addRecipientChip(contact: ContactSuggestion) {
        val chip = Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Input).apply {
            text = if (contact.isRawNumber) contact.phoneNumber else contact.name
            isCloseIconVisible = true
            // Show contact photo if available
            if (!contact.photoUri.isNullOrBlank()) {
                try {
                    val uri = android.net.Uri.parse(contact.photoUri)
                    val stream = contentResolver.openInputStream(uri)
                    stream.use { input ->
                        if (input != null) {
                            val bmp = android.graphics.BitmapFactory.decodeStream(input)
                            val rounded = androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(resources, bmp)
                            rounded.isCircular = true
                            chipIcon = rounded
                            isChipIconVisible = true
                        }
                    }
                } catch (_: Exception) { }
            }
            setOnCloseIconClickListener {
                selectedRecipients.removeAll { it.phoneNumber == contact.phoneNumber }
                chipGroup.removeView(this)
                if (selectedRecipients.isEmpty()) {
                    composerContainer.visibility = View.GONE
                    sendButton.isEnabled = false
                } else {
                    sendButton.isEnabled = !messageInput.text.isNullOrBlank()
                }
            }
        }
        chipGroup.addView(chip)
        sendButton.isEnabled = !messageInput.text.isNullOrBlank()
    }

    // Removed old avatar injection logic; chips handle display now.
}
