package com.praveenpuglia.cleansms

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.net.toUri

class ThreadDetailActivity : AppCompatActivity() {

    private lateinit var messagesRecycler: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private var threadId: Long = -1
    private var contactName: String? = null
    private var contactAddress: String? = null
    private var contactPhotoUri: String? = null
    private var messageCategory: MessageCategory = MessageCategory.UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread_detail)

        threadId = intent.getLongExtra("THREAD_ID", -1)
        contactName = intent.getStringExtra("CONTACT_NAME")
        contactAddress = intent.getStringExtra("CONTACT_ADDRESS")
        contactPhotoUri = intent.getStringExtra("CONTACT_PHOTO_URI")
        
        // Get category from intent
        val categoryName = intent.getStringExtra("CATEGORY")
        messageCategory = try {
            if (categoryName != null) MessageCategory.valueOf(categoryName) else MessageCategory.UNKNOWN
        } catch (e: IllegalArgumentException) {
            MessageCategory.UNKNOWN
        }

        if (threadId == -1L) {
            finish()
            return
        }

        setupHeader()
        setupRecyclerView()
        setupComposeBar()
        loadMessages()
    }

    private fun setupHeader() {
        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        // Set up avatar - reuse same logic as ThreadAdapter
        val avatarImage = findViewById<ImageView>(R.id.thread_detail_avatar_image)
        val avatarText = findViewById<TextView>(R.id.thread_detail_avatar_text)
        
        val photoUri = contactPhotoUri
        if (!photoUri.isNullOrEmpty()) {
            try {
                avatarImage.setImageURI(photoUri.toUri())
                avatarImage.visibility = android.view.View.VISIBLE
                avatarText.visibility = android.view.View.GONE
            } catch (_: Exception) {
                // Fall through to initials
                setAvatarInitials(avatarText, avatarImage)
            }
        } else {
            setAvatarInitials(avatarText, avatarImage)
        }
        
        setupHeaderText()
    }
    
    private fun setAvatarInitials(avatarText: TextView, avatarImage: ImageView) {
        // Use contact name if available
        if (!contactName.isNullOrEmpty()) {
            val initial = contactName!!.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
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
        avatarText.text = "#"
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
    }

    private fun setupComposeBar() {
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty() && contactAddress != null) {
                sendMessage(contactAddress!!, messageText)
            }
        }
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
            runOnUiThread {
                messageAdapter.updateMessages(messages)
                // Scroll to bottom after loading
                if (messages.isNotEmpty()) {
                    messagesRecycler.scrollToPosition(messages.size - 1)
                }
            }
        }.start()
    }

    private fun queryMessagesForThread(threadId: Long): List<Message> {
        val uri = "content://sms".toUri()
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type")
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

            while (cursor.moveToNext()) {
                val id = if (idxId >= 0) cursor.getLong(idxId) else -1L
                val thread = if (idxThreadId >= 0) cursor.getLong(idxThreadId) else -1L
                val address = if (idxAddress >= 0) cursor.getString(idxAddress) ?: "" else ""
                val body = if (idxBody >= 0) cursor.getString(idxBody) ?: "" else ""
                val date = if (idxDate >= 0) cursor.getLong(idxDate) else 0L
                val type = if (idxType >= 0) cursor.getInt(idxType) else 1

                messages.add(Message(id, thread, address, body, date, type))
            }
        }
        return messages
    }
}
