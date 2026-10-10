package com.praveenpuglia.cleansms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionInfo
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var sending = false
    private var loadJob: Job? = null

    private var starredIds by mutableStateOf<Set<Long>>(emptySet())
    private var availableSims by mutableStateOf<List<SubscriptionInfo>>(emptyList())
    private var selectedSimIndex by mutableIntStateOf(0)
    private val simSlots by lazy { SimSlots.resolver(this) }

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
        AppCompatDelegate.setDefaultNightMode(AppSettings.getThemeMode(this))
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
        starredIds = StarredMessages.ids(this)
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
                    selectedSimNumber = availableSims.getOrNull(selectedSimIndex)?.simSlotIndex?.plus(1),
                    showSimSelector = availableSims.size > 1,
                    highlightedMessageId = highlightedMessageId,
                    scrollRequest = scrollRequest,
                    starredIds = starredIds,
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
                    onToggleStar = { starredIds = StarredMessages.toggle(this, it.id) },
                    onReportSpam = ::reportSpam,
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

        contactLookupUri?.let { it.toUri() }?.let {
            launchContactIntent(it)
            return
        }

        val info = ContactDirectory.enrich(this, address)
        info?.name?.takeIf(String::isNotBlank)?.let { contactName = it }
        info?.photoUri?.takeIf(String::isNotBlank)?.let { contactPhotoUri = it }

        val resolvedUri = info?.lookupUri?.let { it.toUri() }
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
            startActivity(Intent(Intent.ACTION_DIAL, "tel:$it".toUri()))
        }
    }

    private fun shouldShowComposer(): Boolean {
        if (messageCategory == MessageCategory.PERSONAL) return true
        val address = contactAddress?.trim().orEmpty()
        return address.isNotEmpty() && address.none(Char::isLetter)
    }

    /** The user confirmed the exact complaint text; it must go from the SIM that got the spam. */
    private fun reportSpam(message: Message) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.toast_sms_permission_required, Toast.LENGTH_SHORT).show()
            return
        }
        val complaint = TraiReport.complaint(message.body, message.address, message.date)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { SmsSender.send(this@ThreadDetailActivity, TraiReport.SHORT_CODE, complaint, message.subscriptionId) }
                Toast.makeText(this@ThreadDetailActivity, R.string.toast_trai_report_sent, Toast.LENGTH_SHORT).show()
            } catch (error: RuntimeException) {
                Toast.makeText(this@ThreadDetailActivity, getString(R.string.toast_message_send_failed, error.message.orEmpty()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSimSelector() {
        availableSims = SimSlots.activeSims(this)
        selectedSimIndex = SimSlots.defaultIndex(availableSims)
    }

    private fun sendMessage(address: String, body: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.toast_sms_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (sending) return // a double tap must not send twice
        sending = true
        val selectedSim = availableSims.getOrNull(selectedSimIndex)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { SmsSender.send(this@ThreadDetailActivity, address, body, selectedSim?.subscriptionId, threadId) }
                messageText = ""
                Toast.makeText(this@ThreadDetailActivity, selectedSim?.takeIf { availableSims.size > 1 }
                    ?.let { getString(R.string.toast_message_sent_via_sim, it.simSlotIndex + 1) }
                    ?: getString(R.string.toast_message_sent), Toast.LENGTH_SHORT).show()
                loadMessages()
            } catch (error: RuntimeException) {
                Toast.makeText(this@ThreadDetailActivity, getString(R.string.toast_message_send_failed, error.message.orEmpty()), Toast.LENGTH_SHORT).show()
            } finally {
                sending = false
            }
        }
    }

    private fun loadMessages() {
        loadJob?.cancel() // newest load wins
        loadJob = lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                queryMessagesForThread(threadId).also { markThreadAsRead(threadId) }
            }
            messages = loaded
            val target = targetMessageId
            if (target != null) {
                highlightedMessageId = loaded.firstOrNull { it.id == target }?.id ?: loaded.lastOrNull()?.id
                highlightInProgress = highlightedMessageId != null
                targetMessageId = null
            }
            scrollRequest++
        }
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
                    simSlot = subscriptionId?.let(simSlots::slotFor),
                    status = if (status >= 0) cursor.getInt(status) else -1,
                )
            }
        }
        return messages
    }

    private fun markThreadAsRead(id: Long) {
        try {
            contentResolver.update(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply { put(Telephony.Sms.READ, 1) },
                "thread_id = ? AND read = 0",
                arrayOf(id.toString()),
            )
        } catch (error: RuntimeException) {
            android.util.Log.w("ThreadDetailActivity", "Failed to mark thread read: ${error.javaClass.simpleName}")
        }
    }

    companion object {
        fun intent(
            context: Context,
            threadId: Long,
            address: String,
            contactName: String?,
            photoUri: String?,
            lookupUri: String?,
            category: MessageCategory,
            targetMessageId: Long? = null,
        ): Intent = Intent(context, ThreadDetailActivity::class.java)
            .putExtra(EXTRA_THREAD_ID, threadId)
            .putExtra(EXTRA_CONTACT_ADDRESS, address)
            .putExtra(EXTRA_CONTACT_NAME, contactName)
            .putExtra(EXTRA_CONTACT_PHOTO_URI, photoUri)
            .putExtra(EXTRA_CONTACT_LOOKUP_URI, lookupUri)
            .putExtra(EXTRA_CATEGORY, category.name)
            .apply { targetMessageId?.let { putExtra(EXTRA_TARGET_MESSAGE_ID, it) } }

        private const val OBSERVER_DEBOUNCE_MS = 300L
        private const val STATE_MESSAGE = "message"
        private const val EXTRA_THREAD_ID = "THREAD_ID"
        private const val EXTRA_CONTACT_NAME = "CONTACT_NAME"
        private const val EXTRA_CONTACT_ADDRESS = "CONTACT_ADDRESS"
        private const val EXTRA_CONTACT_PHOTO_URI = "CONTACT_PHOTO_URI"
        private const val EXTRA_CONTACT_LOOKUP_URI = "CONTACT_LOOKUP_URI"
        private const val EXTRA_CATEGORY = "CATEGORY"
        private const val EXTRA_TARGET_MESSAGE_ID = "TARGET_MESSAGE_ID"
    }
}
