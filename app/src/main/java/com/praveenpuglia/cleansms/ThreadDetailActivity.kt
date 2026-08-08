package com.praveenpuglia.cleansms

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailScreen

class ThreadDetailActivity : AppCompatActivity() {
    private var threadId = -1L
    private var messages by mutableStateOf<List<Message>>(emptyList())
    private var messageText by mutableStateOf("")
    private var contactName by mutableStateOf<String?>(null)
    private var contactAddress: String? = null
    private var contactPhotoUri by mutableStateOf<String?>(null)
    private var contactLookupUri: String? = null
    private var messageCategory = MessageCategory.UNKNOWN
    private var targetMessageId: Long? = null
    private var highlightedMessageId by mutableStateOf<Long?>(null)
    private var scrollRequest by mutableIntStateOf(0)
    private var highlightInProgress = false

    private var availableSims by mutableStateOf<List<SubscriptionInfo>>(emptyList())
    private var selectedSimIndex by mutableIntStateOf(0)
    private val simSlotCache = mutableMapOf<Int, Int?>()
    private val subscriptionFallbackOrder = mutableListOf<Int>()

    private val observerHandler = Handler(Looper.getMainLooper())
    private var pendingObserverReload: Runnable? = null
    private val smsObserver = object : ContentObserver(observerHandler) {
        override fun onChange(selfChange: Boolean) {
            pendingObserverReload?.let(observerHandler::removeCallbacks)
            pendingObserverReload = Runnable {
                if (!highlightInProgress) loadMessages()
            }.also { observerHandler.postDelayed(it, OBSERVER_DEBOUNCE_MS) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        super.onCreate(savedInstanceState)

        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId == -1L) {
            finish()
            return
        }

        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        contactAddress = intent.getStringExtra(EXTRA_CONTACT_ADDRESS)
        contactPhotoUri = intent.getStringExtra(EXTRA_CONTACT_PHOTO_URI)
        contactLookupUri = intent.getStringExtra(EXTRA_CONTACT_LOOKUP_URI)
        messageCategory = intent.getStringExtra(EXTRA_CATEGORY)?.let {
            runCatching { MessageCategory.valueOf(it) }.getOrDefault(MessageCategory.UNKNOWN)
        } ?: MessageCategory.UNKNOWN
        targetMessageId = intent.getLongExtra(EXTRA_TARGET_MESSAGE_ID, -1L).takeIf { it != -1L }
        messageText = savedInstanceState?.getString(STATE_MESSAGE).orEmpty()

        setupSimSelector()
        setContent {
            CleanSmsTheme {
                ThreadDetailScreen(
                    contactName = contactName,
                    contactAddress = contactAddress,
                    contactPhotoUri = contactPhotoUri,
                    category = messageCategory,
                    messages = messages,
                    messageText = messageText,
                    showComposer = shouldShowComposer(),
                    focusComposer = intent.getBooleanExtra(EXTRA_FOCUS_COMPOSER, false),
                    selectedSimNumber = availableSims.getOrNull(selectedSimIndex)?.simSlotIndex?.plus(1),
                    showSimSelector = availableSims.size > 1,
                    highlightedMessageId = highlightedMessageId,
                    scrollRequest = scrollRequest,
                    onBack = ::finish,
                    onAvatarClick = ::openContactFromHeader,
                    onCall = ::openDialer,
                    onMessageChange = { messageText = it },
                    onSimToggle = { selectedSimIndex = (selectedSimIndex + 1) % availableSims.size },
                    onSend = {
                        val address = contactAddress
                        val body = messageText.trim()
                        if (address != null && body.isNotEmpty()) sendMessage(address, body)
                    },
                    onHighlightFinished = {
                        highlightInProgress = false
                        highlightedMessageId = null
                    },
                )
            }
        }
        loadMessages()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_MESSAGE, messageText)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
    }

    override fun onPause() {
        pendingObserverReload?.let(observerHandler::removeCallbacks)
        pendingObserverReload = null
        contentResolver.unregisterContentObserver(smsObserver)
        super.onPause()
    }

    private fun hasContactsPermission() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun openContactFromHeader() {
        val address = contactAddress
        if (address.isNullOrBlank()) {
            Toast.makeText(this, R.string.toast_contact_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasContactsPermission()) {
            Toast.makeText(this, R.string.toast_contact_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        contactLookupUri?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let {
            launchContactIntent(it)
            return
        }

        val info = MainActivity.lookupFromCache(address)
            ?: MainActivity.lookupFromIndex(address)
            ?: ContactEnrichment.enrich(this, address)
        info?.name?.takeIf(String::isNotBlank)?.let { contactName = it }
        info?.photoUri?.takeIf(String::isNotBlank)?.let { contactPhotoUri = it }

        val resolvedUri = info?.lookupUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (resolvedUri != null) {
            contactLookupUri = resolvedUri.toString()
            launchContactIntent(resolvedUri)
        } else {
            openAddContactIntent(address)
        }
    }

    private fun launchContactIntent(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.toast_contact_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAddContactIntent(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
        }
        try {
            startActivity(intent)
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.toast_contact_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDialer() {
        contactAddress?.let {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
        }
    }

    private fun shouldShowComposer(): Boolean {
        if (messageCategory == MessageCategory.PERSONAL) return true
        val address = contactAddress?.trim().orEmpty()
        return address.isNotEmpty() && address.none(Char::isLetter)
    }

    private fun setupSimSelector() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        try {
            availableSims = getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            if (availableSims.size > 1) {
                val defaultId = SubscriptionManager.getDefaultSmsSubscriptionId()
                selectedSimIndex = availableSims.indexOfFirst { it.subscriptionId == defaultId }.coerceAtLeast(0)
            }
        } catch (_: SecurityException) {
            availableSims = emptyList()
        }
    }

    private fun sendMessage(address: String, body: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.toast_sms_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val selectedSim = availableSims.getOrNull(selectedSimIndex)
                val smsManager = selectedSim?.let {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(it.subscriptionId)
                } ?: run {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(body)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(address, null, body, null, null)
                }

                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.THREAD_ID, threadId)
                    selectedSim?.let { put("sub_id", it.subscriptionId) }
                }
                contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)

                val simInfo = selectedSim?.takeIf { availableSims.size > 1 }
                    ?.let { " via SIM ${it.simSlotIndex + 1}" }
                    .orEmpty()
                runOnUiThread {
                    messageText = ""
                    Toast.makeText(this, getString(R.string.toast_message_sent, simInfo), Toast.LENGTH_SHORT).show()
                    loadMessages()
                }
            } catch (error: RuntimeException) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_message_send_failed, error.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadMessages() {
        Thread {
            val loaded = queryMessagesForThread(threadId)
            markThreadAsRead(threadId)
            runOnUiThread {
                messages = loaded
                val target = targetMessageId
                if (target != null) {
                    highlightedMessageId = loaded.firstOrNull { it.id == target }?.id ?: loaded.lastOrNull()?.id
                    highlightInProgress = highlightedMessageId != null
                    targetMessageId = null
                }
                scrollRequest++
            }
        }.start()
    }

    private fun queryMessagesForThread(id: Long): List<Message> {
        val messages = mutableListOf<Message>()
        contentResolver.query(
            "content://sms".toUri(),
            arrayOf("_id", "thread_id", "address", "body", "date", "type", "sub_id", "status"),
            "thread_id = ?",
            arrayOf(id.toString()),
            "date ASC",
        )?.use { cursor ->
            val messageId = cursor.getColumnIndex("_id")
            val thread = cursor.getColumnIndex("thread_id")
            val address = cursor.getColumnIndex("address")
            val body = cursor.getColumnIndex("body")
            val date = cursor.getColumnIndex("date")
            val type = cursor.getColumnIndex("type")
            val subId = cursor.getColumnIndex("sub_id")
            val status = cursor.getColumnIndex("status")
            while (cursor.moveToNext()) {
                val rawSubId = if (subId >= 0) cursor.getInt(subId) else -1
                val subscriptionId = rawSubId.takeIf { it >= 0 }
                messages += Message(
                    id = if (messageId >= 0) cursor.getLong(messageId) else -1L,
                    threadId = if (thread >= 0) cursor.getLong(thread) else -1L,
                    address = if (address >= 0) cursor.getString(address).orEmpty() else "",
                    body = if (body >= 0) cursor.getString(body).orEmpty() else "",
                    date = if (date >= 0) cursor.getLong(date) else 0L,
                    type = if (type >= 0) cursor.getInt(type) else 1,
                    subscriptionId = subscriptionId,
                    simSlot = subscriptionId?.let(::resolveSimSlot),
                    status = if (status >= 0) cursor.getInt(status) else -1,
                )
            }
        }
        return messages
    }

    private fun resolveSimSlot(subscriptionId: Int): Int? {
        if (simSlotCache.containsKey(subscriptionId)) return simSlotCache[subscriptionId]
        var slot = if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
                    ?.firstOrNull { it.subscriptionId == subscriptionId }
                    ?.simSlotIndex
                    ?.plus(1)
            } catch (_: SecurityException) {
                null
            }
        } else {
            null
        }
        if (slot == null) {
            if (subscriptionId !in subscriptionFallbackOrder && subscriptionFallbackOrder.size < 2) {
                subscriptionFallbackOrder += subscriptionId
            }
            slot = subscriptionFallbackOrder.indexOf(subscriptionId).takeIf { it >= 0 }?.plus(1)
        }
        simSlotCache[subscriptionId] = slot
        return slot
    }

    private fun markThreadAsRead(id: Long) {
        try {
            contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply { put(Telephony.Sms.READ, 1) },
                "thread_id = ? AND read = 0",
                arrayOf(id.toString()),
            )
            MainActivity.refreshThreadsIfActive()
        } catch (error: RuntimeException) {
            android.util.Log.w("ThreadDetailActivity", "Failed to mark thread read: ${error.javaClass.simpleName}")
        }
    }

    private companion object {
        const val OBSERVER_DEBOUNCE_MS = 300L
        const val STATE_MESSAGE = "message"
        const val EXTRA_THREAD_ID = "THREAD_ID"
        const val EXTRA_CONTACT_NAME = "CONTACT_NAME"
        const val EXTRA_CONTACT_ADDRESS = "CONTACT_ADDRESS"
        const val EXTRA_CONTACT_PHOTO_URI = "CONTACT_PHOTO_URI"
        const val EXTRA_CONTACT_LOOKUP_URI = "CONTACT_LOOKUP_URI"
        const val EXTRA_CATEGORY = "CATEGORY"
        const val EXTRA_TARGET_MESSAGE_ID = "TARGET_MESSAGE_ID"
        const val EXTRA_FOCUS_COMPOSER = "focus_composer"
    }
}
