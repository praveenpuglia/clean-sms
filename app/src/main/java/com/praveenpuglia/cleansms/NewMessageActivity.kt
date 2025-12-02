package com.praveenpuglia.cleansms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.provider.ContactsContract
import android.provider.Telephony
import android.content.ContentValues
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NewMessageActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var chipGroup: ChipGroup
    private lateinit var recipientInput: EditText
    private lateinit var contactsList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var messageCounter: TextView
    private lateinit var smsPartsIndicator: TextView
    private lateinit var simToggle: View
    private lateinit var simNumber: TextView
    
    private lateinit var contactsAdapter: ContactSuggestionAdapter
    private var allContacts: List<ContactSuggestion> = emptyList()
    private val selectedRecipients = mutableListOf<ContactSuggestion>()
    
    // SIM selection
    private var availableSims: List<SubscriptionInfo> = emptyList()
    private var selectedSimIndex: Int = 0 // Index into availableSims list

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before setting content view
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)

        backButton = findViewById(R.id.new_message_back_button)
        chipGroup = findViewById(R.id.new_message_recipients_chip_group)
        recipientInput = findViewById(R.id.new_message_recipient_input)
        contactsList = findViewById(R.id.new_message_contacts_list)
        messageInput = findViewById(R.id.new_message_body_input)
        sendButton = findViewById(R.id.new_message_send_button)
        messageCounter = findViewById(R.id.new_message_counter)
        smsPartsIndicator = findViewById(R.id.new_message_sms_parts)
        simToggle = findViewById(R.id.new_message_sim_toggle)
        simNumber = findViewById(R.id.new_message_sim_number)

        setupViews()
        loadContacts()
        setupSimSelector()
        handleIncomingIntent()
    }

    private fun handleIncomingIntent() {
        // Handle SENDTO intent from contacts app or other apps (e.g., sms:+1234567890?body=message)
        intent?.data?.let { uri ->
            if (uri.scheme?.startsWith("sms") == true || uri.scheme?.startsWith("mms") == true) {
                // Extract phone number and optional body from URI
                val schemeSpecificPart = uri.schemeSpecificPart
                val phoneNumber = schemeSpecificPart.substringBefore('?').trim()
                
                // Extract optional message body from query string
                val bodyText = if (schemeSpecificPart.contains('?')) {
                    val queryString = schemeSpecificPart.substringAfter('?')
                    parseQueryParameter(queryString, "body")
                } else {
                    null
                }

                if (phoneNumber.isNotEmpty()) {
                    val contact = findContactByPhoneNumber(phoneNumber)
                    
                    if (contact != null && selectedRecipients.none { it.phoneNumber == contact.phoneNumber }) {
                        selectedRecipients.add(contact)
                        addRecipientChip(contact)
                        updateSendButtonState()
                    }

                    if (!bodyText.isNullOrBlank()) {
                        messageInput.setText(bodyText)
                        messageInput.setSelection(bodyText.length)
                    }

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
        
        allContacts.firstOrNull { normalizeNumber(it.phoneNumber) == normalizedInput }?.let {
            return it
        }

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

        val hasIncomingRecipient = intent?.data?.let { uri ->
            (uri.scheme?.startsWith("sms") == true || uri.scheme?.startsWith("mms") == true) &&
            uri.schemeSpecificPart.substringBefore('?').trim().isNotEmpty()
        } ?: false

        if (!hasIncomingRecipient) {
            messageInput.post {
                messageInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        // Setup contacts list
        contactsAdapter = ContactSuggestionAdapter(emptyList()) { contact ->
            onContactSelected(contact)
        }
        contactsList.layoutManager = LinearLayoutManager(this)
        contactsList.adapter = contactsAdapter

        // Setup recipient input
        recipientInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterContacts(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Backspace to remove last chip
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
                    updateSendButtonState()
                    return@setOnKeyListener true
                }
            }
            false
        }

        // Setup message input with SMS counting
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
                updateSmsCounter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Initial counter update
        updateSmsCounter("")

        // Setup send button
        sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun setupSimSelector() {
        // Check if we have phone state permission for SIM info
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simToggle.visibility = View.GONE
            return
        }

        try {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeSubscriptions = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            
            if (activeSubscriptions.size > 1) {
                availableSims = activeSubscriptions
                
                // Default to system default SMS SIM
                val defaultSmsSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
                selectedSimIndex = availableSims.indexOfFirst { it.subscriptionId == defaultSmsSubId }
                if (selectedSimIndex < 0) selectedSimIndex = 0
                
                simToggle.visibility = View.VISIBLE
                updateSimToggleDisplay()
                
                // Simple toggle - just flip between SIMs with animation
                simToggle.setOnClickListener {
                    animateSimToggle {
                        selectedSimIndex = (selectedSimIndex + 1) % availableSims.size
                        updateSimToggleDisplay()
                    }
                }
            } else {
                simToggle.visibility = View.GONE
            }
        } catch (e: SecurityException) {
            simToggle.visibility = View.GONE
        } catch (e: Exception) {
            simToggle.visibility = View.GONE
        }
    }

    private fun updateSimToggleDisplay() {
        if (availableSims.isNotEmpty() && selectedSimIndex < availableSims.size) {
            val sim = availableSims[selectedSimIndex]
            simNumber.text = (sim.simSlotIndex + 1).toString()
        }
    }

    private fun animateSimToggle(onMidpoint: () -> Unit) {
        val duration = 150L
        // Slide up and fade out
        simNumber.animate()
            .translationY(-simNumber.height.toFloat())
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                onMidpoint()
                // Reset position to below and slide up
                simNumber.translationY = simNumber.height.toFloat()
                simNumber.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(duration)
                    .start()
            }
            .start()
    }

    private fun updateSmsCounter(text: String) {
        val length = text.length
        
        // Check if message contains any non-GSM characters (requires Unicode/UCS-2 encoding)
        val isUnicode = text.any { !isGsmCharacter(it) }
        
        // SMS limits:
        // GSM 7-bit: 160 chars for single, 153 per part for concatenated
        // Unicode (UCS-2): 70 chars for single, 67 per part for concatenated
        val singleLimit = if (isUnicode) 70 else 160
        val multiPartLimit = if (isUnicode) 67 else 153
        
        val parts = when {
            length == 0 -> 0
            length <= singleLimit -> 1
            else -> ((length - 1) / multiPartLimit) + 1
        }
        
        val remaining = when {
            length == 0 -> singleLimit
            length <= singleLimit -> singleLimit - length
            else -> {
                val usedInCurrentPart = ((length - 1) % multiPartLimit) + 1
                multiPartLimit - usedInCurrentPart
            }
        }
        
        // Update counter display
        if (parts <= 1) {
            messageCounter.text = "$length / $singleLimit"
            smsPartsIndicator.visibility = View.GONE
        } else {
            messageCounter.text = "$remaining"
            smsPartsIndicator.text = "$parts SMS"
            smsPartsIndicator.visibility = View.VISIBLE
        }
        
        // Change color if approaching/exceeding limit
        val counterColor = when {
            parts > 1 -> ContextCompat.getColor(this, R.color.md_theme_light_tertiary)
            length > singleLimit * 0.9 -> ContextCompat.getColor(this, R.color.md_theme_light_error)
            else -> ContextCompat.getColor(this, android.R.color.darker_gray)
        }
        messageCounter.setTextColor(counterColor)
        smsPartsIndicator.setTextColor(counterColor)
    }

    private fun isGsmCharacter(c: Char): Boolean {
        // GSM 7-bit default alphabet characters
        // Basic Latin + some special chars + extended via escape
        val gsmBasic = "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
                       "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        val gsmExtended = "^{}\\[~]|€"
        return c in gsmBasic || c in gsmExtended
    }

    private fun updateSendButtonState() {
        sendButton.isEnabled = !messageInput.text.isNullOrBlank() && selectedRecipients.isNotEmpty()
    }

    private fun loadContacts() {
        val contacts = mutableListOf<ContactSuggestion>()
        val seen = HashSet<String>()

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
        if (selectedRecipients.any { it.phoneNumber == contact.phoneNumber }) {
            recipientInput.setText("")
            return
        }
        selectedRecipients.add(contact)
        addRecipientChip(contact)
        recipientInput.setText("")
        contactsList.visibility = View.GONE
        updateSendButtonState()
    }

    private fun sendMessage() {
        if (selectedRecipients.isEmpty()) return
        val messageText = messageInput.text.toString().trim()
        if (messageText.isBlank()) return

        try {
            // Get the appropriate SmsManager for selected SIM
            val smsManager = if (availableSims.isNotEmpty() && selectedSimIndex < availableSims.size) {
                val subscriptionId = availableSims[selectedSimIndex].subscriptionId
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            selectedRecipients.forEach { recipient ->
                // Handle long messages by splitting
                val parts = smsManager.divideMessage(messageText)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(recipient.phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(recipient.phoneNumber, null, messageText, null, null)
                }
                
                // Insert into sent folder
                try {
                    val threadId = Telephony.Threads.getOrCreateThreadId(this, setOf(recipient.phoneNumber))
                    val values = ContentValues().apply {
                        put("address", recipient.phoneNumber)
                        put("body", messageText)
                        put("date", System.currentTimeMillis())
                        put("read", 1)
                        put("type", 2)
                        put("thread_id", threadId)
                        // Include subscription ID if using specific SIM
                        if (availableSims.isNotEmpty() && selectedSimIndex < availableSims.size) {
                            put("sub_id", availableSims[selectedSimIndex].subscriptionId)
                        }
                    }
                    contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                } catch (_: SecurityException) { }
            }
            
            val simInfo = if (availableSims.size > 1) {
                " via SIM ${availableSims[selectedSimIndex].simSlotIndex + 1}"
            } else ""
            
            Toast.makeText(
                this, 
                if (selectedRecipients.size == 1) "Message sent$simInfo" else "Messages sent$simInfo", 
                Toast.LENGTH_SHORT
            ).show()
            
            MainActivity.refreshThreadsIfActive()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun normalizeNumber(number: String): String = PhoneNumberUtils.normalizeNumber(number).filter { it.isDigit() }

    private fun isPotentialPhoneNumber(input: String): Boolean {
        if (input.isBlank()) return false
        val digits = input.filter { it.isDigit() }
        if (digits.length < 5) return false
        if (digits.toSet().size == 1) return false
        return input.matches(Regex("^[+()0-9 -]{5,}"))
    }

    private fun addRecipientChip(contact: ContactSuggestion) {
        val chip = Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Input).apply {
            text = if (contact.isRawNumber) contact.phoneNumber else contact.name
            isCloseIconVisible = true
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
                updateSendButtonState()
            }
        }
        chipGroup.addView(chip)
        updateSendButtonState()
    }
}
