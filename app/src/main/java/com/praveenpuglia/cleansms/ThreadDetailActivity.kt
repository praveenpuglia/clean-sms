package com.praveenpuglia.cleansms

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

class ThreadDetailActivity : AppCompatActivity() {

    private lateinit var messagesRecycler: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messageCounter: TextView
    private lateinit var composeBarContainer: View
    private lateinit var avatarContainer: View
    private lateinit var avatarImage: ImageView
    private lateinit var avatarText: TextView
    private var threadId: Long = -1
    private var contactName: String? = null
    private var contactAddress: String? = null
    private var contactPhotoUri: String? = null
    private var contactLookupUri: String? = null
    private var messageCategory: MessageCategory = MessageCategory.UNKNOWN
    private var targetMessageId: Long? = null
    
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            loadMessages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before setting content view
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread_detail)

        threadId = intent.getLongExtra("THREAD_ID", -1)
        contactName = intent.getStringExtra("CONTACT_NAME")
        contactAddress = intent.getStringExtra("CONTACT_ADDRESS")
    contactPhotoUri = intent.getStringExtra("CONTACT_PHOTO_URI")
    contactLookupUri = intent.getStringExtra("CONTACT_LOOKUP_URI")
        
        // Get category from intent
        val categoryName = intent.getStringExtra("CATEGORY")
        messageCategory = try {
            if (categoryName != null) MessageCategory.valueOf(categoryName) else MessageCategory.UNKNOWN
        } catch (e: IllegalArgumentException) {
            MessageCategory.UNKNOWN
        }

        val targetId = intent.getLongExtra("TARGET_MESSAGE_ID", -1L)
        targetMessageId = if (targetId != -1L) targetId else null

        if (threadId == -1L) {
            finish()
            return
        }

        setupHeader()
        setupRecyclerView()
    setupComposeBar()
        loadMessages()
        
        // Focus composer if requested
        if (intent.getBooleanExtra("focus_composer", false)) {
            messageInput.post {
                messageInput.requestFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register content observer to watch for SMS changes (delivery status updates)
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver
        )
    }

    override fun onPause() {
        super.onPause()
        // Unregister content observer
        contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        // Set up avatar - reuse same logic as ThreadAdapter
        avatarContainer = findViewById(R.id.thread_detail_avatar_container)
        avatarImage = findViewById(R.id.thread_detail_avatar_image)
        avatarText = findViewById(R.id.thread_detail_avatar_text)

        avatarContainer.setOnClickListener { openContactFromHeader() }
        
        refreshAvatar()
        
        setupHeaderText()
    }

    private fun setAvatarInitials() {
        val label = when {
            !contactName.isNullOrBlank() -> contactName!!.trim()
            !contactAddress.isNullOrBlank() -> contactAddress!!.trim()
            else -> "#"
        }
        val initial = label.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
        AvatarColorResolver.applyTo(avatarText, label)
        // Use contact name if available
        if (!contactName.isNullOrEmpty()) {
            avatarText.text = initial
            avatarText.visibility = android.view.View.VISIBLE
            avatarImage.visibility = android.view.View.GONE
            return
        }

        // Fallback: if address contains letters (alphanumeric sender ID), show first letter
        val raw = contactAddress ?: ""
        val hasLetters = raw.any { it.isLetter() }
        if (hasLetters) {
            val firstLetter = raw.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
            avatarText.text = firstLetter
            avatarText.visibility = android.view.View.VISIBLE
            avatarImage.visibility = android.view.View.GONE
            return
        }

        // Final fallback for phone numbers
        avatarText.text = initial
        avatarText.visibility = android.view.View.VISIBLE
        avatarImage.visibility = android.view.View.GONE
    }
    
    private fun setupHeaderText() {
        android.util.Log.d("ThreadDetail", "=== setupHeaderText called ===")
        findViewById<TextView>(R.id.thread_contact_name).text = contactName ?: contactAddress ?: "Unknown"
        if (contactName != null && contactAddress != null) {
            findViewById<TextView>(R.id.thread_contact_number).text = contactAddress
        } else {
            findViewById<TextView>(R.id.thread_contact_number).visibility = android.view.View.GONE
        }
        
        // Show call button only for PERSONAL category
        val callButton = findViewById<ImageButton>(R.id.call_button)
        android.util.Log.d("ThreadDetail", "Category: $messageCategory, Address: $contactAddress")
        if (messageCategory == MessageCategory.PERSONAL && !contactAddress.isNullOrEmpty()) {
            android.util.Log.d("ThreadDetail", "Showing call button")
            callButton.visibility = android.view.View.VISIBLE
            callButton.setOnClickListener {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$contactAddress")
                }
                startActivity(dialIntent)
            }
        } else {
            android.util.Log.d("ThreadDetail", "Hiding call button")
            callButton.visibility = android.view.View.GONE
        }
    }

    private fun setupRecyclerView() {
        messagesRecycler = findViewById(R.id.messages_recycler)
        messagesRecycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Start from bottom
        }
        messageAdapter = MessageAdapter(emptyList())
        messagesRecycler.adapter = messageAdapter
        
        // Add sticky day header decoration
        messagesRecycler.addItemDecoration(StickyDayHeaderDecoration(messageAdapter))
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun openContactFromHeader() {
        val address = contactAddress
        if (address.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasContactsPermission()) {
            Toast.makeText(this, getString(R.string.toast_contact_permission_required), Toast.LENGTH_SHORT).show()
            return
        }

        val existingUri = contactLookupUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (existingUri != null) {
            launchContactIntent(existingUri)
            return
        }

        val cachedInfo = MainActivity.lookupFromCache(address)
            ?: MainActivity.lookupFromIndex(address)
            ?: ContactEnrichment.enrich(this, address)

        if (cachedInfo != null) {
            var shouldRefreshAvatar = false
            if (!cachedInfo.name.isNullOrBlank() && cachedInfo.name != contactName) {
                contactName = cachedInfo.name
                setupHeaderText()
                shouldRefreshAvatar = true
            }
            if (!cachedInfo.photoUri.isNullOrBlank() && cachedInfo.photoUri != contactPhotoUri) {
                contactPhotoUri = cachedInfo.photoUri
                shouldRefreshAvatar = true
            }
            if (shouldRefreshAvatar) {
                refreshAvatar()
            }
        }

        val resolvedUri = cachedInfo?.lookupUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (resolvedUri != null) {
            contactLookupUri = resolvedUri.toString()
            launchContactIntent(resolvedUri)
        } else {
            Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAvatar() {
        val photoUri = contactPhotoUri
        if (!photoUri.isNullOrEmpty()) {
            try {
                avatarImage.setImageURI(photoUri.toUri())
                avatarImage.visibility = View.VISIBLE
                avatarText.visibility = View.GONE
                return
            } catch (_: Exception) {
                // Ignore and fall back to initials
            }
        }
        setAvatarInitials()
    }

    private fun launchContactIntent(contactUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, contactUri)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupComposeBar() {
        composeBarContainer = findViewById(R.id.composer_bar_container)
        messageInput = findViewById(R.id.composer_message_input)
        sendButton = findViewById(R.id.composer_send_button)
        messageCounter = findViewById(R.id.composer_message_counter)

        // Update counter as user types
        messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                messageCounter.text = s?.length?.toString() ?: "0"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty() && contactAddress != null) {
                sendMessage(contactAddress!!, messageText)
            }
        }

        updateComposeBarVisibility()
    }

    private fun updateComposeBarVisibility() {
        val showComposer = shouldShowComposer()
        composeBarContainer.visibility = if (showComposer) View.VISIBLE else View.GONE
    }

    private fun shouldShowComposer(): Boolean {
        if (messageCategory == MessageCategory.PERSONAL) return true
        val address = contactAddress?.trim().orEmpty()
        if (address.isEmpty()) return false
        val hasLetter = address.any { it.isLetter() }
        return !hasLetter
    }

    private fun sendMessage(address: String, messageText: String) {
        // Check permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                // Send via SmsManager
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(address, null, messageText, null, null)

                // Insert into provider as sent message
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, messageText)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
                contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)

                runOnUiThread {
                    messageInput.text.clear()
                    Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
                    // Reload messages to show the sent message
                    loadMessages()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadMessages() {
        Thread {
            val messages = queryMessagesForThread(threadId)
            markThreadAsRead(threadId)
            runOnUiThread {
                messageAdapter.updateMessages(messages)
                val targetId = targetMessageId
                if (targetId != null) {
                    val index = messages.indexOfFirst { it.id == targetId }
                    if (index >= 0) {
                        messagesRecycler.scrollToPosition(index)
                        highlightMessage(index)
                    } else if (messages.isNotEmpty()) {
                        val lastIndex = messages.size - 1
                        messagesRecycler.scrollToPosition(lastIndex)
                        highlightMessage(lastIndex)
                    }
                    targetMessageId = null
                } else if (messages.isNotEmpty()) {
                    val lastIndex = messages.size - 1
                    messagesRecycler.scrollToPosition(lastIndex)
                }
            }
        }.start()
    }

    private fun highlightMessage(position: Int) {
        messagesRecycler.post { highlightMessageInternal(position, 0) }
    }

    private fun highlightMessageInternal(position: Int, attempt: Int) {
        val holder = messagesRecycler.findViewHolderForAdapterPosition(position)
        if (holder == null) {
            if (attempt < 5) {
                messagesRecycler.postDelayed({ highlightMessageInternal(position, attempt + 1) }, 48)
            }
            return
        }
        val targetView = holder.itemView.findViewById<android.view.View>(R.id.message_container) ?: holder.itemView
        val fallbackColor = ContextCompat.getColor(this, R.color.md_theme_light_primaryContainer)
        val highlightColor = MaterialColors.getColor(targetView, com.google.android.material.R.attr.colorPrimaryContainer, fallbackColor)
        val cornerRadius = targetView.resources.getDimension(R.dimen.message_bubble_corner_radius)
        val overlay = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setColor(highlightColor)
            alpha = 0
            setBounds(0, 0, targetView.width, targetView.height)
        }
        targetView.overlay.add(overlay)
        val animator = ValueAnimator.ofInt(0, 220).apply {
            duration = 420
            startDelay = 80
            repeatCount = 1
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { overlay.alpha = it.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    targetView.overlay.remove(overlay)
                }
            })
        }
        animator.start()
    }


    private fun queryMessagesForThread(threadId: Long): List<Message> {
        val uri = "content://sms".toUri()
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type", "sub_id", "status")
        val selection = "thread_id = ?"
        val selectionArgs = arrayOf(threadId.toString())
        val sortOrder = "date ASC"

        val messages = mutableListOf<Message>()
        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idxId = cursor.getColumnIndex("_id")
            val idxThreadId = cursor.getColumnIndex("thread_id")
            val idxAddress = cursor.getColumnIndex("address")
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxType = cursor.getColumnIndex("type")
            val idxSubId = cursor.getColumnIndex("sub_id")
            val idxStatus = cursor.getColumnIndex("status")

            while (cursor.moveToNext()) {
                val id = if (idxId >= 0) cursor.getLong(idxId) else -1L
                val thread = if (idxThreadId >= 0) cursor.getLong(idxThreadId) else -1L
                val address = if (idxAddress >= 0) cursor.getString(idxAddress) ?: "" else ""
                val body = if (idxBody >= 0) cursor.getString(idxBody) ?: "" else ""
                val date = if (idxDate >= 0) cursor.getLong(idxDate) else 0L
                val type = if (idxType >= 0) cursor.getInt(idxType) else 1
                val subRaw = if (idxSubId >= 0) cursor.getInt(idxSubId) else -1
                val subscriptionId = if (subRaw >= 0) subRaw else null
                val simSlot = subscriptionId?.let { resolveSimSlot(it) }
                val status = if (idxStatus >= 0) cursor.getInt(idxStatus) else -1
                messages.add(Message(id, thread, address, body, date, type, subscriptionId, simSlot, status))
            }
        }
        return messages
    }

    private val simSlotCache = mutableMapOf<Int, Int?>()
    private val subscriptionFallbackOrder = mutableListOf<Int>()
    private fun resolveSimSlot(subscriptionId: Int): Int? {
        if (simSlotCache.containsKey(subscriptionId)) return simSlotCache[subscriptionId]
        val hasPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        var slot: Int? = null
        if (hasPhoneState) {
            val mgr = getSystemService(SubscriptionManager::class.java)
            val info = try { mgr?.activeSubscriptionInfoList?.firstOrNull { it.subscriptionId == subscriptionId } } catch (_: SecurityException) { null }
            slot = info?.simSlotIndex?.plus(1)
        }
        if (slot == null) {
            if (!subscriptionFallbackOrder.contains(subscriptionId) && subscriptionFallbackOrder.size < 2) {
                subscriptionFallbackOrder += subscriptionId
            }
            slot = subscriptionFallbackOrder.indexOf(subscriptionId).takeIf { it >= 0 }?.plus(1)
        }
        simSlotCache[subscriptionId] = slot
        return slot
    }

    private fun markThreadAsRead(threadId: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI,
                values,
                "thread_id = ? AND read = 0",
                arrayOf(threadId.toString())
            )
            MainActivity.refreshThreadsIfActive()
        } catch (e: Exception) {
            android.util.Log.w("ThreadDetailActivity", "Failed to mark thread read: ${e.message}")
        }
    }
}
