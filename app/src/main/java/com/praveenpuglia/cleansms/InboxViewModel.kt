package com.praveenpuglia.cleansms

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.praveenpuglia.cleansms.ui.inbox.InboxPage
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inbox state and SMS provider work. Lives across configuration changes, so tabs, filters, search
 * and selection survive rotation; the Activity keeps navigation, onboarding and permissions.
 */
class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application get() = getApplication()
    private val resolver get() = app.contentResolver
    private val simSlots = SimSlots.resolver(application)
    private val otpFetchLimit = 200
    private val categories = listOf(
        MessageCategory.PERSONAL,
        MessageCategory.TRANSACTIONAL,
        MessageCategory.SERVICE,
        MessageCategory.PROMOTIONAL,
        MessageCategory.GOVERNMENT,
    )

    var pages by mutableStateOf<List<InboxPage>>(emptyList()); private set
    var selectedPageIndex by mutableIntStateOf(0); private set
    var scrollToTopRequest by mutableIntStateOf(0); private set
    var allThreads by mutableStateOf<List<ThreadItem>>(emptyList()); private set
    var otpMessages by mutableStateOf<List<OtpMessageItem>>(emptyList()); private set
    var allTabItems by mutableStateOf<List<SearchResultItem>>(emptyList()); private set
    var unreadOnly by mutableStateOf(false); private set
    var selectionMode by mutableStateOf(false); private set
    var selectedThreadIds by mutableStateOf<Set<Long>>(emptySet()); private set
    var selectedMessageIds by mutableStateOf<Set<Long>>(emptySet()); private set
    var showDeleteDialog by mutableStateOf(false)
    var searchMode by mutableStateOf(false); private set
    var searchQuery by mutableStateOf(""); private set
    var searchResults by mutableStateOf<List<SearchResultItem>>(emptyList()); private set
    private var allMessagesForSearch: List<SearchResultItem> = emptyList()

    private var configuredAllTab: Boolean? = null
    private var inboxLoad: Job? = null
    private var inboxReloadPending = false
    private var allTabLoad: Job? = null
    private var searchLoad: Job? = null
    private var searchDebounce: Job? = null

    /** Builds the pages once; again only when the All-tab setting changed (then lands on the default tab). */
    fun configure(allTabEnabled: Boolean, defaultTab: AppSettings.DefaultTab) {
        if (configuredAllTab == allTabEnabled) return
        configuredAllTab = allTabEnabled
        pages = buildPagerPages(allTabEnabled)
        selectedPageIndex = getInitialPageIndexForTab(defaultTab)
        pruneSelection()
    }

    /**
     * Query the SMS content provider and build a list of ThreadItem where each thread appears once
     * with the most recent message's date and snippet. We query content://sms sorted by date desc
     * and pick the first message we see for each thread_id.
     */
    private fun loadSmsThreads(): List<ThreadItem> {
        val uri: Uri = "content://sms".toUri()
        val projection = arrayOf("thread_id", "address", "date", "body", "read")
        val sortOrder = "date DESC"
        val cursor: Cursor? = resolver.query(uri, projection, null, null, sortOrder)
        val map = LinkedHashMap<Long, ThreadItem>()
        val unreadCounts = mutableMapOf<Long, Int>()
        val threadsWithSpam = mutableSetOf<Long>()

        cursor?.use { c ->
            val idxThread = c.getColumnIndex("thread_id")
            val idxAddress = c.getColumnIndex("address")
            val idxDate = c.getColumnIndex("date")
            val idxBody = c.getColumnIndex("body")
            val idxRead = c.getColumnIndex("read")

            while (c.moveToNext()) {
                val threadId = if (idxThread >= 0) c.getLong(idxThread) else -1L
                val body = if (idxBody >= 0) c.getString(idxBody) ?: "" else ""
                
                // Check if this message is spam
                if (SpamDetector.isSpam(body)) {
                    threadsWithSpam.add(threadId)
                }
                
                if (!map.containsKey(threadId)) {
                    val address = if (idxAddress >= 0) c.getString(idxAddress) ?: "Unknown" else "Unknown"
                    val date = if (idxDate >= 0) c.getLong(idxDate) else 0L
                    
                    // Categorize the thread
                    val category = CategoryStorage.getCategoryOrCompute(app, address, threadId)
                    
                    map[threadId] = ThreadItem(threadId, address, date, body, category = category)
                }
                val isUnread = idxRead >= 0 && c.getInt(idxRead) == 0
                if (isUnread) {
                    unreadCounts[threadId] = (unreadCounts[threadId] ?: 0) + 1
                }
            }
        }

        return map.values.map { item ->
            val unread = unreadCounts[item.threadId] ?: 0
            val hasSpam = threadsWithSpam.contains(item.threadId)
            item.copy(unreadCount = unread, hasSpam = hasSpam)
        }
    }

    private fun loadOtpMessages(): List<OtpMessageItem> {
        val uri: Uri = "content://sms".toUri()
    val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read", "sub_id")
        val selection = "type = ?"
        val selectionArgs = arrayOf("1") // Inbox / received messages
        val sortOrder = "date DESC"

        val results = mutableListOf<OtpMessageItem>()

        resolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idxId = cursor.getColumnIndex("_id")
            val idxThread = cursor.getColumnIndex("thread_id")
            val idxAddress = cursor.getColumnIndex("address")
            val idxBody = cursor.getColumnIndex("body")
            val idxDate = cursor.getColumnIndex("date")
            val idxRead = cursor.getColumnIndex("read")
            val idxSubId = cursor.getColumnIndex("sub_id")

            while (cursor.moveToNext() && results.size < otpFetchLimit) {
                val body = if (idxBody >= 0) cursor.getString(idxBody) ?: "" else ""
                val otpCode = extractOtpFromBody(body)
                if (otpCode != null) {
                    val messageId = if (idxId >= 0) cursor.getLong(idxId) else -1L
                    val threadId = if (idxThread >= 0) cursor.getLong(idxThread) else -1L
                    val address = if (idxAddress >= 0) {
                        cursor.getString(idxAddress)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    } else "Unknown"
                    val date = if (idxDate >= 0) cursor.getLong(idxDate) else 0L
                    val isRead = idxRead >= 0 && cursor.getInt(idxRead) != 0
                    if (messageId != -1L && threadId != -1L) {
                        val subIdRaw = if (idxSubId >= 0) cursor.getInt(idxSubId) else -1
                        val subscriptionId = if (subIdRaw >= 0) subIdRaw else null
                        val simSlot = subscriptionId?.let(simSlots::slotFor)
                        results.add(
                            OtpMessageItem(
                                messageId = messageId,
                                threadId = threadId,
                                address = address,
                                body = body,
                                date = date,
                                otpCode = otpCode,
                                subscriptionId = subscriptionId,
                                simSlot = simSlot,
                                isRead = isRead
                            )
                        )
                    }
                }
            }
        }

        return results
    }

    private fun extractOtpFromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return CategoryClassifier.extractHighPrecisionOtp(body)
    }

    private fun queryAllMessagesForSearch(threadsById: Map<Long, ThreadItem>): List<SearchResultItem> {
        val uri = "content://sms".toUri()
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read", "sub_id")
        val sortOrder = "date DESC"

        val results = mutableListOf<SearchResultItem>()

        try {
            resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idxId = cursor.getColumnIndex("_id")
                val idxThreadId = cursor.getColumnIndex("thread_id")
                val idxAddress = cursor.getColumnIndex("address")
                val idxBody = cursor.getColumnIndex("body")
                val idxDate = cursor.getColumnIndex("date")
                val idxRead = cursor.getColumnIndex("read")
                val idxSubId = cursor.getColumnIndex("sub_id")

                while (cursor.moveToNext()) {
                    val messageId = if (idxId >= 0) cursor.getLong(idxId) else continue
                    val threadId = if (idxThreadId >= 0) cursor.getLong(idxThreadId) else -1L
                    val address = if (idxAddress >= 0) cursor.getString(idxAddress) ?: "" else ""
                    val body = if (idxBody >= 0) cursor.getString(idxBody) ?: "" else ""
                    val date = if (idxDate >= 0) cursor.getLong(idxDate) else 0L
                    val read = if (idxRead >= 0) cursor.getInt(idxRead) else 1
                    val subIdRaw = if (idxSubId >= 0) cursor.getInt(idxSubId) else -1
                    val subscriptionId = if (subIdRaw >= 0) subIdRaw else null
                    val simSlot = subscriptionId?.let(simSlots::slotFor)

                    if (body.isBlank()) continue

                    // Contact info and category come from the already-enriched thread list
                    val existingThread = threadsById[threadId]
                    val contactName = existingThread?.contactName
                    val contactPhotoUri = existingThread?.contactPhotoUri
                    val contactLookupUri = existingThread?.contactLookupUri
                    val category = existingThread?.category ?: MessageCategory.UNKNOWN

                    results.add(SearchResultItem(
                        messageId = messageId,
                        threadId = threadId,
                        sender = address,
                        senderDisplay = contactName,
                        body = body,
                        date = date,
                        contactPhotoUri = contactPhotoUri,
                        contactLookupUri = contactLookupUri,
                        category = category,
                        isUnread = read == 0,
                        subscriptionId = subscriptionId,
                        simSlot = simSlot
                    ))
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to query messages for search: ${e.javaClass.simpleName}")
        } catch (e: SQLiteException) {
            Log.w(TAG, "Failed to query messages for search: ${e.javaClass.simpleName}")
        }

        return results
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            // Show all messages in chronological order when no query
            updateSearchResults(allMessagesForSearch.sortedByDescending { it.date })
        } else {
            // Perform fuzzy search
            val results = FuzzySearch.search(allMessagesForSearch, query)
            updateSearchResults(results)
        }
    }

    private fun updateSearchResults(results: List<SearchResultItem>) {
        searchResults = results
    }

    fun startThreadSelection(item: ThreadItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedThreadIds = emptySet()
            selectedMessageIds = emptySet()
        }
        selectedThreadIds = selectedThreadIds + item.threadId
    }

    fun startOtpSelection(item: OtpMessageItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedThreadIds = emptySet()
            selectedMessageIds = emptySet()
        }
        selectedMessageIds = selectedMessageIds + item.messageId
    }

    fun toggleThreadSelection(item: ThreadItem) {
        if (!selectionMode) {
            startThreadSelection(item)
            return
        }
        selectedThreadIds = if (item.threadId in selectedThreadIds) selectedThreadIds - item.threadId else selectedThreadIds + item.threadId
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
        }
    }

    fun toggleOtpSelection(item: OtpMessageItem) {
        if (!selectionMode) {
            startOtpSelection(item)
            return
        }
        selectedMessageIds = if (item.messageId in selectedMessageIds) selectedMessageIds - item.messageId else selectedMessageIds + item.messageId
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
        }
    }

    private fun updateSelectionUi() {
        if (selectionMode && selectionCount() == 0) exitSelectionMode()
    }

    fun exitSelectionMode() {
        if (!selectionMode && selectedThreadIds.isEmpty() && selectedMessageIds.isEmpty()) return
        selectionMode = false
        selectedThreadIds = emptySet()
        selectedMessageIds = emptySet()
    }

    fun toggleSelectAll() {
        if (!selectionMode) return

        val currentPage = pages.getOrNull(selectedPageIndex) ?: return

        // Get filtered items based on unread filter
        val filteredThreads = if (unreadOnly) {
            allThreads.filter { it.hasUnread }
        } else {
            allThreads
        }
        val filteredOtp = if (unreadOnly) {
            otpMessages.filter { it.isUnread }
        } else {
            otpMessages
        }

        when (currentPage) {
            is InboxPage.All -> {
                // No selection mode on the All page (chronological view, mirrors search).
            }
            is InboxPage.Otp -> {
                val allOtpIds = filteredOtp.map { it.messageId }.toSet()
                val allSelected = allOtpIds.isNotEmpty() && allOtpIds.all { it in selectedMessageIds }

                if (allSelected) {
                    // Deselect all OTP messages
                    selectedMessageIds = selectedMessageIds - allOtpIds
                    if (selectionCount() == 0) {
                        exitSelectionMode()
                        return
                    }
                } else {
                    // Select all OTP messages
                    selectedMessageIds = selectedMessageIds + allOtpIds
                }
            }
            is InboxPage.CategoryPage -> {
                val categoryThreads = filteredThreads.filter { it.category == currentPage.category }
                val allThreadIdsInCategory = categoryThreads.map { it.threadId }.toSet()
                val allSelected = allThreadIdsInCategory.isNotEmpty() && allThreadIdsInCategory.all { it in selectedThreadIds }

                if (allSelected) {
                    // Deselect all threads in this category
                    selectedThreadIds = selectedThreadIds - allThreadIdsInCategory
                    if (selectionCount() == 0) {
                        exitSelectionMode()
                        return
                    }
                } else {
                    // Select all threads in this category
                    selectedThreadIds = selectedThreadIds + allThreadIdsInCategory
                }
            }
        }
    }

    fun confirmDeleteSelection() {
        val totalSelected = selectionCount()
        if (totalSelected == 0) {
            exitSelectionMode()
            return
        }
        showDeleteDialog = true
    }

    fun markSelectionAsRead() {
        val threadIds = selectedThreadIds.toSet()
        val messageIds = selectedMessageIds.toSet()
        if (threadIds.isEmpty() && messageIds.isEmpty()) {
            exitSelectionMode()
            return
        }

        // Leave selection now, not when the write finishes: a late exit would wipe a new selection
        // the user started in the meantime. The ids are already captured above.
        exitSelectionMode()
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val values = ContentValues(1).apply { put(Telephony.Sms.READ, 1) }
                try {
                    threadIds.forEach { threadId ->
                        resolver.update(
                            Telephony.Sms.CONTENT_URI,
                            values,
                            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                            arrayOf(threadId.toString()),
                        )
                    }
                    messageIds.forEach { messageId ->
                        resolver.update(
                            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                            values,
                            "${Telephony.Sms.READ} = 0",
                            null,
                        )
                    }
                    true
                } catch (error: SecurityException) {
                    Log.w(TAG, "Failed to mark selection read: ${error.javaClass.simpleName}")
                    false
                } catch (error: IllegalArgumentException) {
                    Log.w(TAG, "Failed to mark selection read: ${error.javaClass.simpleName}")
                    false
                }
            }
            Toast.makeText(
                app,
                app.getString(if (success) R.string.toast_messages_marked_read else R.string.toast_messages_mark_read_failed),
                Toast.LENGTH_SHORT,
            ).show()
            reload()
        }
    }

    fun performDeletion() {
        showDeleteDialog = false
        val threadIds = selectedThreadIds.toSet()
        val messageIds = selectedMessageIds.toSet()
        if (threadIds.isEmpty() && messageIds.isEmpty()) {
            exitSelectionMode()
            return
        }
        val messageToThread = otpMessages.associate { it.messageId to it.threadId }
        val filteredMessageIds = messageIds.filter { id ->
            val threadId = messageToThread[id]
            threadId == null || !threadIds.contains(threadId)
        }

        exitSelectionMode() // see markSelectionAsRead
        viewModelScope.launch {
            val deletedCount = withContext(Dispatchers.IO) {
                val uris = threadIds.map { ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, it) } +
                    filteredMessageIds.map { ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, it) }
                uris.sumOf { uri ->
                    try {
                        resolver.delete(uri, null, null).coerceAtLeast(0)
                    } catch (e: RuntimeException) {
                        // One failed row must not abort the rest of a bulk delete.
                        Log.w(TAG, "Failed to delete selection item: ${e.javaClass.simpleName}")
                        0
                    }
                }
            }
            val message = if (deletedCount > 0) R.string.toast_messages_deleted else R.string.toast_messages_delete_failed
            Toast.makeText(app, app.getString(message), Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    private fun pruneSelection(): Boolean {
        var changed = false
        val validThreadIds = allThreads.map { it.threadId }.toSet()
        val validMessageIds = otpMessages.map { it.messageId }.toSet()
        val retainedThreads = selectedThreadIds.intersect(validThreadIds)
        val retainedMessages = selectedMessageIds.intersect(validMessageIds)
        if (retainedThreads != selectedThreadIds) {
            selectedThreadIds = retainedThreads
            changed = true
        }
        if (retainedMessages != selectedMessageIds) {
            selectedMessageIds = retainedMessages
            changed = true
        }
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
            return true
        }
        return changed
    }

    fun setUnreadFilter(enabled: Boolean) {
        unreadOnly = enabled
        updatePagerContent()
    }

    fun selectPage(index: Int) {
        if (index !in pages.indices) return
        if (selectedPageIndex == index) {
            scrollToTopRequest++
        } else {
            selectedPageIndex = index
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        searchDebounce?.cancel()
        searchDebounce = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(query)
        }
    }

    fun enterSearchMode() {
        searchMode = true
        loadAllMessagesForSearch()
    }

    fun exitSearchMode() {
        searchMode = false
        searchDebounce?.cancel()
        searchQuery = ""
        searchResults = emptyList()
    }

    private fun loadAllItemsForAllTab() {
        val unreadOnly = unreadOnly
        val threadsById = allThreads.associateBy { it.threadId }
        allTabLoad?.cancel()
        allTabLoad = viewModelScope.launch {
            allTabItems = withContext(Dispatchers.IO) {
                queryAllMessagesForSearch(threadsById)
                    .let { if (unreadOnly) it.filter { item -> item.isUnread } else it }
                    .sortedByDescending { it.date }
            }
        }
    }

    private fun loadAllMessagesForSearch() {
        val threadsById = allThreads.associateBy { it.threadId }
        searchLoad?.cancel()
        searchLoad = viewModelScope.launch {
            val messages = withContext(Dispatchers.IO) { queryAllMessagesForSearch(threadsById) }
            allMessagesForSearch = messages
            // Show all messages initially in chronological order
            updateSearchResults(messages.sortedByDescending { it.date })
        }
    }

    /**
     * Loads are coalesced: never two at once, and a request arriving mid-load triggers exactly
     * one more pass afterwards, so the newest provider state always wins.
     */
    fun reload() {
        if (inboxLoad?.isActive == true) {
            inboxReloadPending = true
            return
        }
        inboxLoad = viewModelScope.launch {
            do {
                inboxReloadPending = false
                val (threads, otp) = withContext(Dispatchers.IO) {
                    val enrichedThreads = loadSmsThreads().map { t ->
                        val hit = ContactDirectory.resolve(app, t.nameOrAddress)?.takeIf(ContactInfo::hasAny)
                        // No contact: a bundled brand logo (verified DLT owner) fills the avatar.
                        hit?.let { t.copy(contactName = it.name, contactPhotoUri = it.photoUri, contactLookupUri = it.lookupUri) }
                            ?: t.copy(contactPhotoUri = SenderBrands.logoUri(app, t.nameOrAddress))
                    }
                    val enrichedOtp = loadOtpMessages().map { item ->
                        val hit = ContactDirectory.resolve(app, item.address)?.takeIf(ContactInfo::hasAny)
                        hit?.let { item.copy(contactName = it.name, contactPhotoUri = it.photoUri, contactLookupUri = it.lookupUri) }
                            ?: item.copy(contactPhotoUri = SenderBrands.logoUri(app, item.address))
                    }
                    enrichedThreads to enrichedOtp
                }
                allThreads = threads
                otpMessages = otp
                updatePagerContent()
            } while (inboxReloadPending)
        }
    }

    private fun updatePagerContent() {
        pruneSelection()
        if (pages.any { it is InboxPage.All }) loadAllItemsForAllTab()
        updateSelectionUi()
    }

    private fun getInitialPageIndexForTab(defaultTab: AppSettings.DefaultTab): Int {
        return when (defaultTab) {
            AppSettings.DefaultTab.OTP -> {
                pages.indexOfFirst { it is InboxPage.Otp }.takeIf { it >= 0 } ?: 0
            }
            AppSettings.DefaultTab.PERSONAL -> {
                pages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.PERSONAL
                }.takeIf { it >= 0 } ?: 0
            }
            AppSettings.DefaultTab.TRANSACTIONAL -> {
                pages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.TRANSACTIONAL
                }.takeIf { it >= 0 } ?: 0
            }
            AppSettings.DefaultTab.SERVICE -> {
                pages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.SERVICE
                }.takeIf { it >= 0 } ?: 0
            }
            AppSettings.DefaultTab.PROMOTIONAL -> {
                pages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.PROMOTIONAL
                }.takeIf { it >= 0 } ?: 0
            }
            AppSettings.DefaultTab.GOVERNMENT -> {
                pages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.GOVERNMENT
                }.takeIf { it >= 0 } ?: 0
            }
            AppSettings.DefaultTab.ALL -> {
                pages.indexOfFirst { it is InboxPage.All }.takeIf { it >= 0 } ?: 0
            }
        }
    }

    private fun buildPagerPages(allTabEnabled: Boolean): List<InboxPage> {
        val base = listOf(InboxPage.Otp) + categories.map { InboxPage.CategoryPage(it) }
        return if (allTabEnabled) listOf(InboxPage.All) + base else base
    }

    private fun selectionCount(): Int = selectedThreadIds.size + selectedMessageIds.size

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val TAG = "InboxViewModel"
    }
}
