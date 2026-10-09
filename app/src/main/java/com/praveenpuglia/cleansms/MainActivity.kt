package com.praveenpuglia.cleansms

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ContentUris
import android.provider.Telephony
import android.app.role.RoleManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import android.provider.ContactsContract
import com.praveenpuglia.cleansms.ui.inbox.InboxPage
import com.praveenpuglia.cleansms.ui.inbox.InboxScreen
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingScreen
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingUiState
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        // Weak-ish reference for receiver to trigger refresh without leaking context
        private var activeInstance: MainActivity? = null

        fun refreshThreadsIfActive() {
            activeInstance?.refreshThreadsAsync()
        }
    }
    private val requestSmsRoleLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { _ ->
        // Re-evaluate default status after user interaction
        setupDefaultSmsUi()
    }

    private val PERMISSION_REQUEST_CODE = 100

    // We'll ask for a few permissions, but only READ_SMS is required to show the threads list
    private val requestedPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE // needed to reliably map subscriptionId to SIM slot
    )

    private val otpFetchLimit = 200
    private val simSlots by lazy { SimSlots.resolver(this) }
    
    // SharedPreferences for persistent onboarding state
    private val PREFS_NAME = "CleanSmsPrefs"
    private val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
    private var onboardingUiState by mutableStateOf(OnboardingUiState())
    
    // Category filtering state - will be initialized in onCreate
    private lateinit var selectedCategory: MessageCategory
    private var allThreads by mutableStateOf<List<ThreadItem>>(emptyList())
    private var otpMessages by mutableStateOf<List<OtpMessageItem>>(emptyList())
    private var allTabItems by mutableStateOf<List<SearchResultItem>>(emptyList())
    private var initialPageApplied = false
    private val categories = listOf(
        MessageCategory.PERSONAL,
        MessageCategory.TRANSACTIONAL,
        MessageCategory.SERVICE,
        MessageCategory.PROMOTIONAL,
        MessageCategory.GOVERNMENT
    )
    private var pagerPages: List<InboxPage> = buildPagerPages(allTabEnabled = false)
    private var lastAppliedAllTabEnabled: Boolean = false
    private var lastAppliedFontFamily: SettingsActivity.FontFamily = SettingsActivity.FontFamily.SANS_SERIF
    private var selectedPageIndex by mutableIntStateOf(0)
    private var scrollToTopRequest by mutableIntStateOf(0)
    private var permissionRequired by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(true)
    private var showDeleteDialog by mutableStateOf(false)
    private var promoMuted by mutableStateOf(false)

    private fun buildPagerPages(allTabEnabled: Boolean): List<InboxPage> {
        val base = listOf(InboxPage.Otp) + categories.map { InboxPage.CategoryPage(it) }
        return if (allTabEnabled) listOf(InboxPage.All) + base else base
    }
    private var selectionMode by mutableStateOf(false)
    private var selectedThreadIds by mutableStateOf<Set<Long>>(emptySet())
    private var selectedMessageIds by mutableStateOf<Set<Long>>(emptySet())
    
    // Search state
    private var isSearchMode by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var allMessagesForSearch: List<SearchResultItem> = emptyList()
    private var searchResults by mutableStateOf<List<SearchResultItem>>(emptyList())
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val searchDebounceMs = 300L

    // Unread filter state
    private var unreadOnlyFilter by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        super.onCreate(savedInstanceState)

        selectedCategory = getInitialCategory()
        lastAppliedAllTabEnabled = SettingsActivity.getAllTabEnabled(this)
        lastAppliedFontFamily = SettingsActivity.getFontFamily(this)
        pagerPages = buildPagerPages(lastAppliedAllTabEnabled)
        selectedPageIndex = getInitialPageIndexForTab(SettingsActivity.getDefaultTab(this))

        setContent {
            CleanSmsTheme {
                if (showOnboarding) {
                    OnboardingScreen(
                        state = onboardingUiState,
                        onSetDefault = ::requestDefaultSmsRole,
                        onAllowBackground = ::requestBatteryOptimizationExemption,
                        onContinue = ::completeOnboarding,
                    )
                } else {
                    InboxScreen(
                        pages = pagerPages,
                        selectedPageIndex = selectedPageIndex,
                        scrollToTopRequest = scrollToTopRequest,
                        allThreads = allThreads,
                        otpMessages = otpMessages,
                        allItems = allTabItems,
                        searchResults = searchResults,
                        searchMode = isSearchMode,
                        searchQuery = searchQuery,
                        unreadOnly = unreadOnlyFilter,
                        selectionMode = selectionMode,
                        selectedThreadIds = selectedThreadIds,
                        selectedMessageIds = selectedMessageIds,
                        promoMuted = promoMuted,
                        permissionRequired = permissionRequired,
                        showDeleteDialog = showDeleteDialog,
                        onPageSelected = ::selectPage,
                        onSearchModeChange = { enabled -> if (enabled) enterSearchMode() else exitSearchMode() },
                        onSearchQueryChange = ::updateSearchQuery,
                        onUnreadOnlyChange = ::setUnreadFilter,
                        onOpenStats = { startActivity(Intent(this, StatsActivity::class.java)) },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                        onNewMessage = { startActivity(Intent(this, NewMessageActivity::class.java)) },
                        onThreadClick = ::handleThreadClick,
                        onThreadAvatarClick = ::handleThreadAvatarClick,
                        onThreadLongClick = ::startThreadSelection,
                        onOtpClick = ::handleOtpClick,
                        onOtpAvatarClick = ::handleOtpAvatarClick,
                        onOtpLongClick = ::startOtpSelection,
                        onCopyOtp = ::copyOtp,
                        onMessageClick = ::handleSearchResultClick,
                        onSelectAll = ::toggleSelectAll,
                        onMarkAsRead = ::markSelectionAsRead,
                        onDeleteRequest = ::confirmDeleteSelection,
                        onDeleteConfirm = ::performDeletion,
                        onDeleteDismiss = { showDeleteDialog = false },
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isSearchMode -> exitSearchMode()
                    selectionMode -> exitSelectionMode()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
        setupDefaultSmsUi()
    }

    override fun onResume() {
        super.onResume()
        activeInstance = this
        // Re-check after potential default change
        setupDefaultSmsUi()
        // If the All-tab preference changed in Settings, simplest path is to rebuild the activity.
        if (SettingsActivity.getAllTabEnabled(this) != lastAppliedAllTabEnabled ||
            SettingsActivity.getFontFamily(this) != lastAppliedFontFamily) {
            recreate()
            return
        }
        if (hasReadPermission()) {
            refreshThreadsAsync()
        }
        promoMuted = !SettingsActivity.getPromoNotificationsEnabled(this)
    }

    override fun onPause() {
        super.onPause()
        if (activeInstance === this) activeInstance = null
    }

    override fun onDestroy() {
        searchRunnable?.let(searchHandler::removeCallbacks)
        super.onDestroy()
    }

    private fun setupDefaultSmsUi() {
        val telephonyDefault = Telephony.Sms.getDefaultSmsPackage(this)
        val roleManager = getSystemService(RoleManager::class.java)
        val roleHeld = try { roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && roleManager.isRoleHeld(RoleManager.ROLE_SMS) } catch (_: Exception) { false }
        val isDefault = DefaultSmsHelper.isDefaultSmsApp(this)
        val powerManager = getSystemService(PowerManager::class.java)
        val isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        onboardingUiState = OnboardingUiState(isDefault, isBatteryOptimizationIgnored)
        
        // Check if onboarding has been completed (persisted)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false)
        
        Log.d("DefaultSmsUI", "telephonyDefault=$telephonyDefault roleHeld=$roleHeld helper=$isDefault pkg=${packageName} batteryIgnored=$isBatteryOptimizationIgnored hasCompletedOnboarding=$hasCompletedOnboarding")
        
        if (!isDefault || !hasCompletedOnboarding) {
            showOnboarding = true
            permissionRequired = false
        } else {
            showOnboarding = false
            if (hasReadPermission()) {
                showThreadsUi()
            } else {
                showInstructionsUi()
                ActivityCompat.requestPermissions(this, requestedPermissions, PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun requestDefaultSmsRole() {
        try {
            val roleManager = getSystemService(RoleManager::class.java)
            val intent = if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
            }
            requestSmsRoleLauncher.launch(intent)
        } catch (e: RuntimeException) {
            Log.w("DefaultSms", "Role request failed: ${e.message}")
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (e: RuntimeException) {
            Log.w("BatteryOptimization", "Request failed: ${e.message}")
            Toast.makeText(this, R.string.toast_battery_optimization_settings, Toast.LENGTH_LONG).show()
        }
    }

    private fun completeOnboarding() {
        if (!onboardingUiState.isDefaultSmsApp || !onboardingUiState.isBatteryOptimizationIgnored) return

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ONBOARDING_COMPLETED, true)
            .apply()
        showOnboarding = false

        if (hasReadPermission()) {
            showThreadsUi()
        } else {
            showInstructionsUi()
            ActivityCompat.requestPermissions(this, requestedPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun refreshThreadsAsync() {
        reloadInboxData()
    }

    private fun reloadInboxData() {
        val restorePageIndex = if (initialPageApplied) selectedPageIndex else null
        Thread {
            val threads = loadSmsThreads()
            val otpRaw = loadOtpMessages()

            val enrichedThreads = threads.map { t ->
                val hit = ContactDirectory.resolve(this, t.nameOrAddress)?.takeIf(ContactInfo::hasAny)
                hit?.let { t.copy(contactName = it.name, contactPhotoUri = it.photoUri, contactLookupUri = it.lookupUri) } ?: t
            }
            val enrichedOtp = otpRaw.map { item ->
                val hit = ContactDirectory.resolve(this, item.address)?.takeIf(ContactInfo::hasAny)
                hit?.let { item.copy(contactName = it.name, contactPhotoUri = it.photoUri, contactLookupUri = it.lookupUri) } ?: item
            }

            runOnUiThread {
                allThreads = enrichedThreads
                otpMessages = enrichedOtp
                updatePagerContent(restorePageIndex)
                applyInitialPageIfNeeded()
            }
        }.start()
    }

    private fun showThreadsUi() {
        permissionRequired = false
        reloadInboxData()
    }

    private fun updatePagerContent(restorePageIndex: Int?) {
        pruneSelection()
        if (pagerPages.any { it is InboxPage.All }) loadAllItemsForAllTab()
        updateSelectionUi()
        val restoreIndex = restorePageIndex
        if (restoreIndex != null && restoreIndex in pagerPages.indices) {
            selectedPageIndex = restoreIndex
            return
        }
        if (!initialPageApplied) {
            return
        }
        val desiredIndex = pagerPages.indexOfFirst { page ->
            page is InboxPage.CategoryPage && page.category == selectedCategory
        }
        if (desiredIndex >= 0 && pagerPages.getOrNull(selectedPageIndex) is InboxPage.CategoryPage) selectedPageIndex = desiredIndex
    }

    private fun getInitialCategory(): MessageCategory {
        val defaultTab = SettingsActivity.getDefaultTab(this)
        return when (defaultTab) {
            SettingsActivity.DefaultTab.PERSONAL -> MessageCategory.PERSONAL
            SettingsActivity.DefaultTab.TRANSACTIONAL -> MessageCategory.TRANSACTIONAL
            SettingsActivity.DefaultTab.SERVICE -> MessageCategory.SERVICE
            SettingsActivity.DefaultTab.PROMOTIONAL -> MessageCategory.PROMOTIONAL
            SettingsActivity.DefaultTab.GOVERNMENT -> MessageCategory.GOVERNMENT
            SettingsActivity.DefaultTab.OTP -> MessageCategory.PERSONAL // fallback for OTP
            SettingsActivity.DefaultTab.ALL -> MessageCategory.PERSONAL // fallback for All
        }
    }

    private fun getInitialPageIndexForTab(defaultTab: SettingsActivity.DefaultTab): Int {
        return when (defaultTab) {
            SettingsActivity.DefaultTab.OTP -> {
                pagerPages.indexOfFirst { it is InboxPage.Otp }.takeIf { it >= 0 } ?: 0
            }
            SettingsActivity.DefaultTab.PERSONAL -> {
                pagerPages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.PERSONAL
                }.takeIf { it >= 0 } ?: 0
            }
            SettingsActivity.DefaultTab.TRANSACTIONAL -> {
                pagerPages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.TRANSACTIONAL
                }.takeIf { it >= 0 } ?: 0
            }
            SettingsActivity.DefaultTab.SERVICE -> {
                pagerPages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.SERVICE
                }.takeIf { it >= 0 } ?: 0
            }
            SettingsActivity.DefaultTab.PROMOTIONAL -> {
                pagerPages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.PROMOTIONAL
                }.takeIf { it >= 0 } ?: 0
            }
            SettingsActivity.DefaultTab.GOVERNMENT -> {
                pagerPages.indexOfFirst { page ->
                    page is InboxPage.CategoryPage && page.category == MessageCategory.GOVERNMENT
                }.takeIf { it >= 0 } ?: 0
            }
            SettingsActivity.DefaultTab.ALL -> {
                pagerPages.indexOfFirst { it is InboxPage.All }.takeIf { it >= 0 } ?: 0
            }
        }
    }

    private fun applyInitialPageIfNeeded() {
        if (initialPageApplied) return
        initialPageApplied = true
    }

    private fun openThreadDetail(threadItem: ThreadItem, targetMessageId: Long? = null) {
        startActivity(
            ThreadDetailActivity.intent(
                context = this,
                threadId = threadItem.threadId,
                address = threadItem.nameOrAddress,
                contactName = threadItem.contactName,
                photoUri = threadItem.contactPhotoUri,
                lookupUri = threadItem.contactLookupUri,
                category = threadItem.category,
                targetMessageId = targetMessageId,
            ),
        )
    }

    private fun openThreadDetailFromOtp(item: OtpMessageItem) {
        val existingThread = allThreads.firstOrNull { it.threadId == item.threadId }
        val threadItem = if (existingThread != null) {
            existingThread.copy(
                contactName = existingThread.contactName ?: item.contactName,
                contactPhotoUri = existingThread.contactPhotoUri ?: item.contactPhotoUri,
                contactLookupUri = existingThread.contactLookupUri ?: item.contactLookupUri
            )
        } else {
            val category = CategoryStorage.getCategoryOrCompute(this, item.address, item.threadId)
            ThreadItem(
                threadId = item.threadId,
                nameOrAddress = item.address,
                date = item.date,
                snippet = item.body,
                contactName = item.contactName,
                contactPhotoUri = item.contactPhotoUri,
                contactLookupUri = item.contactLookupUri,
                category = category
            )
        }
        openThreadDetail(threadItem, item.messageId)
    }

    private fun handleThreadClick(item: ThreadItem) {
        if (selectionMode) {
            toggleThreadSelection(item)
        } else {
            openThreadDetail(item)
        }
    }

    private fun handleThreadAvatarClick(item: ThreadItem) {
        if (selectionMode) {
            toggleThreadSelection(item)
            return
        }
        if (item.hasSavedContact) {
            openContactFromThread(item)
        } else if (item.category == MessageCategory.PERSONAL) {
            // Only offer add-to-contacts for Personal category
            openAddContactIntent(item.nameOrAddress)
        }
    }

    private fun openAddContactIntent(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOtpClick(item: OtpMessageItem) {
        if (selectionMode) {
            toggleOtpSelection(item)
        } else {
            openThreadDetailFromOtp(item)
        }
    }

    private fun handleOtpAvatarClick(item: OtpMessageItem) {
        if (selectionMode) {
            toggleOtpSelection(item)
            return
        }
        if (item.hasSavedContact) {
            openContactFromOtp(item)
        }
        // OTP messages are service messages - no add-to-contacts for unknown senders
    }

    private fun startThreadSelection(item: ThreadItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedThreadIds = emptySet()
            selectedMessageIds = emptySet()
        }
        selectedThreadIds = selectedThreadIds + item.threadId
    }

    private fun startOtpSelection(item: OtpMessageItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedThreadIds = emptySet()
            selectedMessageIds = emptySet()
        }
        selectedMessageIds = selectedMessageIds + item.messageId
    }

    private fun toggleThreadSelection(item: ThreadItem) {
        if (!selectionMode) {
            startThreadSelection(item)
            return
        }
        selectedThreadIds = if (item.threadId in selectedThreadIds) selectedThreadIds - item.threadId else selectedThreadIds + item.threadId
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
        }
    }

    private fun toggleOtpSelection(item: OtpMessageItem) {
        if (!selectionMode) {
            startOtpSelection(item)
            return
        }
        selectedMessageIds = if (item.messageId in selectedMessageIds) selectedMessageIds - item.messageId else selectedMessageIds + item.messageId
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
        }
    }

    private fun selectionCount(): Int = selectedThreadIds.size + selectedMessageIds.size

    private fun updateSelectionUi() {
        if (selectionMode && selectionCount() == 0) exitSelectionMode()
    }

    private fun exitSelectionMode() {
        if (!selectionMode && selectedThreadIds.isEmpty() && selectedMessageIds.isEmpty()) return
        selectionMode = false
        selectedThreadIds = emptySet()
        selectedMessageIds = emptySet()
    }

    private fun toggleSelectAll() {
        if (!selectionMode) return

        val currentPage = pagerPages.getOrNull(selectedPageIndex) ?: return

        // Get filtered items based on unread filter
        val filteredThreads = if (unreadOnlyFilter) {
            allThreads.filter { it.hasUnread }
        } else {
            allThreads
        }
        val filteredOtp = if (unreadOnlyFilter) {
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

    private fun confirmDeleteSelection() {
        val totalSelected = selectionCount()
        if (totalSelected == 0) {
            exitSelectionMode()
            return
        }
        showDeleteDialog = true
    }

    private fun markSelectionAsRead() {
        val threadIds = selectedThreadIds.toSet()
        val messageIds = selectedMessageIds.toSet()
        if (threadIds.isEmpty() && messageIds.isEmpty()) {
            exitSelectionMode()
            return
        }

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                val values = ContentValues(1).apply { put(Telephony.Sms.READ, 1) }
                try {
                    threadIds.forEach { threadId ->
                        contentResolver.update(
                            Telephony.Sms.CONTENT_URI,
                            values,
                            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                            arrayOf(threadId.toString()),
                        )
                    }
                    messageIds.forEach { messageId ->
                        contentResolver.update(
                            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                            values,
                            "${Telephony.Sms.READ} = 0",
                            null,
                        )
                    }
                    true
                } catch (error: SecurityException) {
                    Log.w("MainActivity", "Failed to mark selection read: ${error.javaClass.simpleName}")
                    false
                } catch (error: IllegalArgumentException) {
                    Log.w("MainActivity", "Failed to mark selection read: ${error.javaClass.simpleName}")
                    false
                }
            }
            exitSelectionMode()
            Toast.makeText(
                this@MainActivity,
                getString(if (success) R.string.toast_messages_marked_read else R.string.toast_messages_mark_read_failed),
                Toast.LENGTH_SHORT,
            ).show()
            refreshThreadsAsync()
        }
    }

    private fun performDeletion() {
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

        Thread {
            var deletedCount = 0
            val resolver = contentResolver
            threadIds.forEach { threadId ->
                try {
                    val uri = ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId)
                    val rows = resolver.delete(uri, null, null)
                    if (rows > 0) deletedCount += rows
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to delete thread $threadId: ${e.message}")
                }
            }
            filteredMessageIds.forEach { messageId ->
                try {
                    val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
                    val rows = resolver.delete(uri, null, null)
                    if (rows > 0) deletedCount += rows
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to delete message $messageId: ${e.message}")
                }
            }
            runOnUiThread {
                val success = deletedCount > 0
                exitSelectionMode()
                if (success) {
                    Toast.makeText(this, getString(R.string.toast_messages_deleted), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.toast_messages_delete_failed), Toast.LENGTH_SHORT).show()
                }
                refreshThreadsAsync()
            }
        }.start()
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

    private fun openContactFromThread(item: ThreadItem) {
        if (!hasContactsPermission() || item.contactName.isNullOrBlank()) return
        openContactForAddress(item.contactLookupUri, item.nameOrAddress, item.contactName, item.contactPhotoUri)
    }

    private fun openContactFromOtp(item: OtpMessageItem) {
        if (!hasContactsPermission() || item.contactName.isNullOrBlank()) return
        openContactForAddress(item.contactLookupUri, item.address, item.contactName, item.contactPhotoUri)
    }

    private fun openContactForAddress(
        lookupUriString: String?,
        rawAddress: String,
        contactName: String?,
        contactPhotoUri: String?
    ) {
        val existingUri = lookupUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (existingUri != null) {
            launchContactIntent(existingUri)
            return
        }
        Thread {
            val resolvedUri = ContactDirectory.findLookupUri(this, rawAddress, contactName, contactPhotoUri)
            if (resolvedUri != null) {
                runOnUiThread { launchContactIntent(resolvedUri) }
            } else {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun launchContactIntent(contactUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, contactUri)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to open contact: ${e.message}")
            Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInstructionsUi() {
        permissionRequired = true
    }

    private fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission() = ContactDirectory.hasPermission(this)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasReadPermission()) {
                showThreadsUi()
            } else {
                showInstructionsUi()
            }
        }
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
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, sortOrder)
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
                    val category = CategoryStorage.getCategoryOrCompute(this, address, threadId)
                    
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

        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
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

    // ========== Overflow Menu ==========
    
    private fun setUnreadFilter(enabled: Boolean) {
        unreadOnlyFilter = enabled
        updatePagerContent(selectedPageIndex)
    }

    private fun selectPage(index: Int) {
        if (index !in pagerPages.indices) return
        if (selectedPageIndex == index) {
            scrollToTopRequest++
        } else {
            selectedPageIndex = index
        }
        (pagerPages[index] as? InboxPage.CategoryPage)?.let { selectedCategory = it.category }
    }

    private fun updateSearchQuery(query: String) {
        searchQuery = query
        searchRunnable?.let(searchHandler::removeCallbacks)
        searchRunnable = Runnable { performSearch(query) }
        searchHandler.postDelayed(searchRunnable!!, searchDebounceMs)
    }

    private fun copyOtp(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        clipboard?.setPrimaryClip(ClipData.newPlainText("OTP", code))
        Toast.makeText(this, R.string.toast_otp_copied, Toast.LENGTH_SHORT).show()
    }

    private fun enterSearchMode() {
        isSearchMode = true
        loadAllMessagesForSearch()
    }

    private fun exitSearchMode() {
        isSearchMode = false
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        searchQuery = ""
        searchResults = emptyList()
    }
    
    private fun loadAllItemsForAllTab() {
        val unreadOnly = unreadOnlyFilter
        Thread {
            val messages = queryAllMessagesForSearch()
                .let { if (unreadOnly) it.filter { item -> item.isUnread } else it }
                .sortedByDescending { it.date }
            runOnUiThread {
                allTabItems = messages
            }
        }.start()
    }

    private fun loadAllMessagesForSearch() {
        Thread {
            val messages = queryAllMessagesForSearch()
            runOnUiThread {
                allMessagesForSearch = messages
                // Show all messages initially in chronological order
                updateSearchResults(messages.sortedByDescending { it.date })
            }
        }.start()
    }
    
    private fun queryAllMessagesForSearch(): List<SearchResultItem> {
        val uri = "content://sms".toUri()
        val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read", "sub_id")
        val sortOrder = "date DESC"

        val results = mutableListOf<SearchResultItem>()

        try {
            contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
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

                    // Try to get contact info from cache or thread
                    val existingThread = allThreads.firstOrNull { it.threadId == threadId }
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
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to query messages for search: ${e.message}")
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
    
    private fun handleSearchResultClick(item: SearchResultItem) {
        // Find or create thread item to navigate
        val existingThread = allThreads.firstOrNull { it.threadId == item.threadId }
        val threadItem = existingThread ?: ThreadItem(
            threadId = item.threadId,
            nameOrAddress = item.sender,
            date = item.date,
            snippet = item.body,
            contactName = item.senderDisplay,
            contactPhotoUri = item.contactPhotoUri,
            contactLookupUri = item.contactLookupUri,
            category = item.category
        )
        
        openThreadDetail(threadItem, item.messageId)
    }
}
