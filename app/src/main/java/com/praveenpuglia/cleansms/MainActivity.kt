package com.praveenpuglia.cleansms

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ContentUris
import android.provider.Telephony
import android.app.role.RoleManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.NumberParseException
import android.provider.ContactsContract
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        // Weak-ish reference for receiver to trigger refresh without leaking context
        private var activeInstance: MainActivity? = null

        fun refreshThreadsIfActive() {
            activeInstance?.refreshThreadsAsync()
        }
        // Static helpers for receiver enrichment
        fun isMobileNumberCandidateStatic(raw: String): Boolean {
            val inst = activeInstance
            if (inst != null) return inst.isMobileNumberCandidate(raw)
            // Fallback heuristic when activity not active (cold start). Mirror main logic in simplified form.
            if (raw.isBlank()) return false
            if (raw.any { it.isLetter() }) return false
            val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(raw)
                .ifEmpty { raw.replace(Regex("\\s+"), "") }
            val digits = normalized.filter { it.isDigit() }
            if (digits.length < 7) return false
            // Treat >=10 digits as likely mobile to allow enrichment attempts, else rely on PhoneLookup directly.
            return digits.length >= 7
        }
        fun lookupFromCache(raw: String): ContactInfo? {
            val inst = activeInstance ?: return null
            val keys = inst.candidateKeysForAddress(raw)
            for (k in keys) {
                val c = inst.contactLookupCache[k]
                if (c != null) return c
            }
            return null
        }
        fun lookupFromIndex(raw: String): ContactInfo? {
            val inst = activeInstance ?: return null
            val idx = inst.bulkContactsIndex ?: return null
            val keys = inst.candidateKeysForAddress(raw)
            for (k in keys) {
                val c = idx[k]
                if (c != null) return c
            }
            return null
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
        Manifest.permission.READ_CONTACTS
    )

    // Cache keyed by E.164 or digits-only for phone numbers; fallback to raw key for alphanumeric senders
    private val contactLookupCache = mutableMapOf<String, ContactInfo>()
    // Bulk in-memory index built once per process run to speed repeated lookups
    private var bulkContactsIndex: Map<String, ContactInfo>? = null
    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val defaultRegion: String by lazy { Locale.getDefault().country.ifEmpty { "US" } }
    private val otpRegex = Regex("\\b\\d{4,8}\\b")
    private val otpKeywordPattern = Regex("\\botp\\b|one[\\s-]*time\\s+password", RegexOption.IGNORE_CASE)
    private val otpFetchLimit = 200
    
    // Category filtering state
    private var selectedCategory: MessageCategory = MessageCategory.PERSONAL
    private var allThreads: List<ThreadItem> = emptyList()
    private var otpMessages: List<OtpMessageItem> = emptyList()
    private var initialPageApplied = false

    private lateinit var categoryTabs: TabLayout
    private lateinit var threadsPager: ViewPager2
    private lateinit var threadsPagerAdapter: ThreadCategoryPagerAdapter
    private lateinit var headerTitle: TextView
    private lateinit var deleteButton: ImageButton
    private var tabLayoutMediator: TabLayoutMediator? = null
    private val categories = listOf(
        MessageCategory.PERSONAL,
        MessageCategory.TRANSACTIONAL,
        MessageCategory.SERVICE,
        MessageCategory.PROMOTIONAL,
        MessageCategory.GOVERNMENT
    )
    private val pagerPages: List<InboxPage> = listOf(InboxPage.Otp) + categories.map { InboxPage.CategoryPage(it) }
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            val page = pagerPages.getOrNull(position)
            if (page is InboxPage.CategoryPage) {
                selectedCategory = page.category
            }
        }
    }

    private var selectionMode: Boolean = false
    private val selectedThreadIds = LinkedHashSet<Long>()
    private val selectedMessageIds = LinkedHashSet<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        categoryTabs = findViewById(R.id.category_tabs)
        threadsPager = findViewById(R.id.threads_pager)
        headerTitle = findViewById(R.id.header_title)
        deleteButton = findViewById(R.id.header_delete_button)
        // TODO: Prevent initial OTP tab flicker during first load.
        threadsPagerAdapter = ThreadCategoryPagerAdapter(
            pagerPages,
            onThreadClick = { threadItem -> handleThreadClick(threadItem) },
            onThreadAvatarClick = { threadItem -> handleThreadAvatarClick(threadItem) },
            onThreadAvatarLongPress = { threadItem -> startThreadSelection(threadItem) },
            onOtpClick = { otpItem -> handleOtpClick(otpItem) },
            onOtpAvatarClick = { otpItem -> handleOtpAvatarClick(otpItem) },
            onOtpAvatarLongPress = { otpItem -> startOtpSelection(otpItem) }
        )
        threadsPager.adapter = threadsPagerAdapter
        threadsPager.registerOnPageChangeCallback(pageChangeCallback)
        tabLayoutMediator = TabLayoutMediator(categoryTabs, threadsPager) { tab, position ->
            tab.text = labelForPage(pagerPages[position])
        }.also { it.attach() }
        categoryTabs.visibility = View.GONE

        deleteButton.setOnClickListener { confirmDeleteSelection() }
        updateSelectionUi()

        setupDefaultSmsUi()

        if (hasReadPermission()) {
            showThreadsUi()
        } else {
            showInstructionsUi()
            ActivityCompat.requestPermissions(this, requestedPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        activeInstance = this
        // Re-check after potential default change
        setupDefaultSmsUi()
        if (hasReadPermission()) {
            refreshThreadsAsync()
        }
    }

    override fun onPause() {
        super.onPause()
        if (activeInstance === this) activeInstance = null
    }

    override fun onBackPressed() {
        if (selectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        tabLayoutMediator?.detach()
        threadsPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroy()
    }

    private fun setupDefaultSmsUi() {
        val telephonyDefault = Telephony.Sms.getDefaultSmsPackage(this)
        val roleManager = getSystemService(RoleManager::class.java)
        val roleHeld = try { roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && roleManager.isRoleHeld(RoleManager.ROLE_SMS) } catch (_: Exception) { false }
        val isDefault = DefaultSmsHelper.isDefaultSmsApp(this)
        Log.d("DefaultSmsUI", "telephonyDefault=$telephonyDefault roleHeld=$roleHeld helper=$isDefault pkg=${packageName}")
        val defaultStatus = findViewById<TextView>(R.id.default_sms_status)
        val setDefaultBtn = findViewById<View>(R.id.set_default_sms_button)
        if (!isDefault) {
            // Show prompt, hide threads until default is set
            defaultStatus.visibility = View.VISIBLE
            setDefaultBtn.visibility = View.VISIBLE
            categoryTabs.visibility = View.GONE
            threadsPager.visibility = View.GONE
            findViewById<TextView>(R.id.permission_instructions).visibility = View.GONE
            defaultStatus.text = "This app is not the default SMS app. Tap below to set it as default."
            setDefaultBtn.setOnClickListener {
                try {
                    val roleManager = getSystemService(RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        requestSmsRoleLauncher.launch(intent)
                    } else {
                        val legacy = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        legacy.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                        requestSmsRoleLauncher.launch(legacy)
                    }
                } catch (e: Exception) {
                    Log.w("DefaultSms", "Role request failed: ${e.message}")
                }
            }
        } else {
            defaultStatus.visibility = View.GONE
            setDefaultBtn.visibility = View.GONE
            // Proceed with permission check/display
            if (hasReadPermission()) {
                showThreadsUi()
            } else {
                showInstructionsUi()
                ActivityCompat.requestPermissions(this, requestedPermissions, PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun refreshThreadsAsync() {
        reloadInboxData()
    }

    private fun reloadInboxData() {
        val restorePageIndex = if (initialPageApplied) threadsPager.currentItem else null
        Thread {
            val threads = loadSmsThreads()
            val otpRaw = loadOtpMessages()

            if (hasContactsPermission() && bulkContactsIndex == null) {
                try {
                    bulkContactsIndex = buildContactsIndex()
                    Log.d("ContactLookup", "Built bulk contacts index with ${bulkContactsIndex?.size ?: 0} entries")
                } catch (e: Exception) {
                    Log.w("MainActivity", "failed to build contacts index: ${e.message}")
                }
            }

            val index = bulkContactsIndex

            val enrichedThreads = if (hasContactsPermission()) {
                threads.map { t ->
                    val hit = resolveContactFromCache(t.nameOrAddress, index)
                    if (hit != null) {
                        val name = hit.name
                        val photo = hit.photoUri
                        val lookup = hit.lookupUri
                        if (name != null || photo != null || lookup != null) {
                            t.copy(contactName = name, contactPhotoUri = photo, contactLookupUri = lookup)
                        } else t
                    } else t
                }
            } else threads

            val enrichedOtp = if (hasContactsPermission()) {
                otpRaw.map { item ->
                    val hit = resolveContactFromCache(item.address, index)
                    if (hit != null) {
                        val name = hit.name
                        val photo = hit.photoUri
                        val lookup = hit.lookupUri
                        if (name != null || photo != null || lookup != null) {
                            item.copy(contactName = name, contactPhotoUri = photo, contactLookupUri = lookup)
                        } else item
                    } else item
                }
            } else otpRaw

            runOnUiThread {
                allThreads = enrichedThreads
                otpMessages = enrichedOtp
                updatePagerContent(restorePageIndex)
                applyInitialPageIfNeeded()
            }
        }.start()
    }

    private fun resolveContactFromCache(
        rawAddress: String,
        index: Map<String, ContactInfo>?
    ): ContactInfo? {
        if (!isMobileNumberCandidate(rawAddress)) return null
        val candidateKeys = candidateKeysForAddress(rawAddress)
        for (key in candidateKeys) {
            val cached = contactLookupCache[key]
            if (cached != null) return cached
            val idxHit = index?.get(key)
            if (idxHit != null) {
                contactLookupCache[key] = idxHit
                return idxHit
            }
        }
        return null
    }

    // Build a simple in-memory index mapping normalized keys to (name, photoUri)
    private fun buildContactsIndex(): Map<String, ContactInfo> {
        val map = mutableMapOf<String, ContactInfo>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )
            val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)
            cursor?.use { c ->
                val idxNumber = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val idxPhoto = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val idxLookup = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val idxContactId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                while (c.moveToNext()) {
                    val phone = if (idxNumber >= 0) c.getString(idxNumber) else null
                    val name = if (idxName >= 0) c.getString(idxName) else null
                    val photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                    val lookupKey = if (idxLookup >= 0) c.getString(idxLookup) else null
                    val contactId = if (idxContactId >= 0) c.getLong(idxContactId) else null
                    val lookupUri = if (!lookupKey.isNullOrEmpty() && contactId != null) {
                        ContactsContract.Contacts.getLookupUri(contactId, lookupKey)?.toString()
                    } else null
                    if (!phone.isNullOrEmpty()) {
                        val key = try {
                            val parsed = phoneUtil.parse(phone, defaultRegion)
                            phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                        } catch (_: Exception) {
                            val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(phone).ifEmpty { phone.replace(Regex("\\s+"), "") }
                            val digits = digitsOnly(normalized)
                            if (digits.isNotEmpty()) digits else phone
                        }
                        map[key] = ContactInfo(name, photo, lookupUri)
                        // also index by raw digits suffixes to help quick suffix matches
                        val digitsOnly = digitsOnly(phone)
                        if (digitsOnly.length >= 7) map[digitsOnly.takeLast(7)] = ContactInfo(name, photo, lookupUri)
                        if (digitsOnly.length >= 9) map[digitsOnly.takeLast(9)] = ContactInfo(name, photo, lookupUri)
                        if (digitsOnly.length >= 10) map[digitsOnly.takeLast(10)] = ContactInfo(name, photo, lookupUri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "bulk index failed: ${e.message}")
        }
        return map
    }

    private fun showThreadsUi() {
        findViewById<TextView>(R.id.permission_instructions).visibility = View.GONE
        categoryTabs.visibility = View.VISIBLE
        threadsPager.visibility = View.VISIBLE
        ViewCompat.requestApplyInsets(threadsPager)
        reloadInboxData()
    }

    private fun updatePagerContent(restorePageIndex: Int?) {
        pruneSelection()
        val grouped = categories.associateWith { category ->
            allThreads.filter { it.category == category }
        }
        threadsPagerAdapter.updateAll(otpMessages, grouped)
        updateSelectionUi()
        updateTabBadges()
        val restoreIndex = restorePageIndex
        if (restoreIndex != null && restoreIndex in pagerPages.indices) {
            if (threadsPager.currentItem != restoreIndex) {
                threadsPager.setCurrentItem(restoreIndex, false)
            }
            return
        }
        if (!initialPageApplied) {
            return
        }
        val desiredIndex = pagerPages.indexOfFirst { page ->
            page is InboxPage.CategoryPage && page.category == selectedCategory
        }
        if (desiredIndex >= 0) {
            val currentPage = pagerPages.getOrNull(threadsPager.currentItem)
            if (currentPage is InboxPage.CategoryPage && threadsPager.currentItem != desiredIndex) {
                threadsPager.setCurrentItem(desiredIndex, false)
            }
        }
    }

    private fun labelForPage(page: InboxPage): String = when (page) {
        InboxPage.Otp -> getString(R.string.tab_otp)
        is InboxPage.CategoryPage -> labelForCategory(page.category)
    }

    private fun applyInitialPageIfNeeded() {
        if (initialPageApplied) return
        val otpIndex = pagerPages.indexOfFirst { it is InboxPage.Otp }
        val desiredIndex = when {
            otpMessages.isNotEmpty() && otpIndex >= 0 -> otpIndex
            else -> pagerPages.indexOfFirst { page ->
                page is InboxPage.CategoryPage && page.category == MessageCategory.PERSONAL
            }.takeIf { it >= 0 } ?: 0
        }
        if (desiredIndex != threadsPager.currentItem && desiredIndex in pagerPages.indices) {
            threadsPager.setCurrentItem(desiredIndex, false)
        }
        initialPageApplied = true
    }

    private fun updateTabBadges() {
        if (categoryTabs.tabCount != pagerPages.size) return
        for (index in pagerPages.indices) {
            val tab = categoryTabs.getTabAt(index) ?: continue
            val hasUnread = when (val page = pagerPages[index]) {
                is InboxPage.Otp -> otpMessages.any { it.isUnread }
                is InboxPage.CategoryPage -> allThreads.any { it.category == page.category && it.hasUnread }
            }
            if (hasUnread) {
                val badge = tab.orCreateBadge
                val fallbackColor = ContextCompat.getColor(this, R.color.md_theme_light_error)
                val badgeColor = MaterialColors.getColor(
                    categoryTabs,
                    com.google.android.material.R.attr.colorError,
                    fallbackColor
                )
                badge.backgroundColor = badgeColor
                if (badge.hasNumber()) badge.clearNumber()
                badge.badgeGravity = BadgeDrawable.TOP_END
                val horizontalOffset = resources.getDimensionPixelOffset(R.dimen.tab_badge_horizontal_offset)
                val verticalOffset = resources.getDimensionPixelOffset(R.dimen.tab_badge_vertical_offset)
                badge.horizontalOffset = horizontalOffset
                badge.verticalOffset = -verticalOffset
                badge.isVisible = true
            } else {
                tab.removeBadge()
            }
        }
    }

    private fun labelForCategory(category: MessageCategory): String = when (category) {
        MessageCategory.PERSONAL -> getString(R.string.category_personal)
        MessageCategory.TRANSACTIONAL -> getString(R.string.category_transactions)
        MessageCategory.SERVICE -> getString(R.string.category_service)
        MessageCategory.PROMOTIONAL -> getString(R.string.category_promotions)
        MessageCategory.GOVERNMENT -> getString(R.string.category_government)
        MessageCategory.UNKNOWN -> getString(R.string.category_unknown)
    }

    private fun openThreadDetail(threadItem: ThreadItem, targetMessageId: Long? = null) {
        val intent = Intent(this, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", threadItem.threadId)
            putExtra("CONTACT_NAME", threadItem.contactName)
            putExtra("CONTACT_ADDRESS", threadItem.nameOrAddress)
            putExtra("CONTACT_PHOTO_URI", threadItem.contactPhotoUri)
            putExtra("CONTACT_LOOKUP_URI", threadItem.contactLookupUri)
            putExtra("CATEGORY", threadItem.category.name)
            if (targetMessageId != null) {
                putExtra("TARGET_MESSAGE_ID", targetMessageId)
            }
        }
        startActivity(intent)
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
    }

    private fun startThreadSelection(item: ThreadItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedThreadIds.clear()
            selectedMessageIds.clear()
        }
        selectedThreadIds.add(item.threadId)
        updateSelectionUi()
    }

    private fun startOtpSelection(item: OtpMessageItem) {
        if (!selectionMode) {
            selectionMode = true
            selectedThreadIds.clear()
            selectedMessageIds.clear()
        }
        selectedMessageIds.add(item.messageId)
        updateSelectionUi()
    }

    private fun toggleThreadSelection(item: ThreadItem) {
        if (!selectionMode) {
            startThreadSelection(item)
            return
        }
        if (!selectedThreadIds.add(item.threadId)) {
            selectedThreadIds.remove(item.threadId)
        }
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
        } else {
            updateSelectionUi()
        }
    }

    private fun toggleOtpSelection(item: OtpMessageItem) {
        if (!selectionMode) {
            startOtpSelection(item)
            return
        }
        if (!selectedMessageIds.add(item.messageId)) {
            selectedMessageIds.remove(item.messageId)
        }
        if (selectionMode && selectionCount() == 0) {
            exitSelectionMode()
        } else {
            updateSelectionUi()
        }
    }

    private fun selectionCount(): Int = selectedThreadIds.size + selectedMessageIds.size

    private fun updateSelectionUi() {
        if (!::headerTitle.isInitialized || !::deleteButton.isInitialized) return
        val count = selectionCount()
        if (selectionMode) {
            headerTitle.text = getString(R.string.selection_count, count)
            deleteButton.visibility = View.VISIBLE
            deleteButton.isEnabled = count > 0
        } else {
            headerTitle.text = getString(R.string.header_messages)
            deleteButton.visibility = View.GONE
            deleteButton.isEnabled = false
        }
        if (::threadsPagerAdapter.isInitialized) {
            threadsPagerAdapter.updateSelectionState(selectionMode, selectedThreadIds, selectedMessageIds)
        }
    }

    private fun exitSelectionMode() {
        if (!selectionMode && selectedThreadIds.isEmpty() && selectedMessageIds.isEmpty()) return
        selectionMode = false
        selectedThreadIds.clear()
        selectedMessageIds.clear()
        updateSelectionUi()
    }

    private fun confirmDeleteSelection() {
        val totalSelected = selectionCount()
        if (totalSelected == 0) {
            exitSelectionMode()
            return
        }
        val message = if (totalSelected == 1) {
            getString(R.string.dialog_delete_message_single)
        } else {
            getString(R.string.dialog_delete_message_multiple, totalSelected)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(message)
            .setNegativeButton(R.string.dialog_delete_negative, null)
            .setPositiveButton(R.string.dialog_delete_positive) { _, _ -> performDeletion() }
            .show()
    }

    private fun performDeletion() {
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
        if (selectedThreadIds.retainAll(validThreadIds)) changed = true
        if (selectedMessageIds.retainAll(validMessageIds)) changed = true
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
            val resolvedUri = findContactLookupUri(rawAddress)
            if (resolvedUri != null) {
                val info = ContactInfo(contactName, contactPhotoUri, resolvedUri.toString())
                val keys = candidateKeysForAddress(rawAddress)
                if (keys.isEmpty()) {
                    contactLookupCache[rawAddress] = info
                } else {
                    for (key in keys) {
                        contactLookupCache[key] = info
                    }
                }
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

    private fun findContactLookupUri(rawAddress: String): Uri? {
        val resolver = contentResolver
        val keys = LinkedHashSet<String>()
        keys += rawAddress
        keys += candidateKeysForAddress(rawAddress)
        for (key in keys) {
            try {
                val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(key))
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup._ID
                )
                resolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idxLookup = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                        val idxId = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                        val lookupKey = if (idxLookup >= 0) cursor.getString(idxLookup) else null
                        val contactId = if (idxId >= 0) cursor.getLong(idxId) else null
                        if (!lookupKey.isNullOrEmpty() && contactId != null) {
                            val contactUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
                            if (contactUri != null) return contactUri
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "findContactLookupUri failed for $key: ${e.message}")
            }
        }
        return null
    }

    private fun cacheKeyForAddress(rawAddress: String): String {
        // reuse class-level phoneUtil/defaultRegion to avoid repeated instantiation
        // Prefer E.164 when possible
        try {
            val parsed = phoneUtil.parse(rawAddress, defaultRegion)
            val e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            if (!e164.isNullOrEmpty()) return e164
        } catch (_: Exception) {
            // ignore
        }
        val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(rawAddress).ifEmpty { rawAddress.replace(Regex("\\s+"), "") }
        val digits = normalized.filter { it.isDigit() }
        return if (digits.isNotEmpty()) digits else rawAddress
    }

    /**
     * Produce a prioritized list of candidate keys to try against the bulk contacts index.
     * Order from most specific to more relaxed to maximize early hits and minimize lookups.
     */
    private fun candidateKeysForAddress(rawAddress: String): List<String> {
        val keys = LinkedHashSet<String>()
        try {
            val parsed = phoneUtil.parse(rawAddress, defaultRegion)
            val e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            if (e164.isNotBlank()) keys += e164

        } catch (_: Exception) {}
        val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(rawAddress).ifEmpty { rawAddress.replace(Regex("\\s+"), "") }
        if (normalized.isNotBlank()) keys += normalized
        val digits = digitsOnly(normalized)
        if (digits.isNotBlank()) keys += digits
        if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            keys += last10
            keys += "+" + last10
            keys += "0" + last10
        }
        if (digits.length >= 9) keys += digits.takeLast(9)
        if (digits.length >= 8) keys += digits.takeLast(8)
        if (digits.length >= 7) keys += digits.takeLast(7)
        return keys.toList()
    }

    /**
     * Heuristic to determine if an address should be treated as a mobile phone number
     * for contact matching. Returns true for digit-like addresses that are long
     * enough to be mobile numbers or parse to a MOBILE number via libphonenumber.
     * Returns false for alphanumeric senders, shortcodes, and obvious service ids.
     */
    private fun isMobileNumberCandidate(rawAddress: String): Boolean {
        if (rawAddress.isBlank()) return false
        // Alphanumeric senders (contain letters) are not phone numbers
        if (rawAddress.any { it.isLetter() }) return false

        // Normalize and count digits quickly
        val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(rawAddress)
            .ifEmpty { rawAddress.replace(Regex("\\s+"), "") }
        val digits = digitsOnly(normalized)

        // Very short codes (e.g., < 7 digits) are usually service/shortcodes
        if (digits.length < 7) return false

        // Try to parse and confirm number type when possible (MOBILE or MOBILE_FAMILY)
        try {
            val parsed = phoneUtil.parse(rawAddress, defaultRegion)
            val type = phoneUtil.getNumberType(parsed)
            return when (type) {
                PhoneNumberUtil.PhoneNumberType.MOBILE,
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE,
                PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER -> true
                else -> {
                    // Fallback: treat reasonably long digit sequences as mobile candidates
                    digits.length >= 10
                }
            }
        } catch (_: Exception) {
            // parsing failed: treat long digit sequences (>=10) as candidate, otherwise skip
            return digits.length >= 10
        }
    }

    private fun showInstructionsUi() {
        findViewById<TextView>(R.id.permission_instructions).visibility = View.VISIBLE
        categoryTabs.visibility = View.GONE
        threadsPager.visibility = View.GONE
    }

    private fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

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

    private sealed class InboxPage {
        object Otp : InboxPage()
        data class CategoryPage(val category: MessageCategory) : InboxPage()
    }

    private inner class ThreadCategoryPagerAdapter(
        private val pages: List<InboxPage>,
        private val onThreadClick: (ThreadItem) -> Unit,
        private val onThreadAvatarClick: (ThreadItem) -> Unit,
        private val onThreadAvatarLongPress: (ThreadItem) -> Unit,
        private val onOtpClick: (OtpMessageItem) -> Unit,
        private val onOtpAvatarClick: (OtpMessageItem) -> Unit,
        private val onOtpAvatarLongPress: (OtpMessageItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val itemsByCategory = pages
            .filterIsInstance<InboxPage.CategoryPage>()
            .associate { it.category to emptyList<ThreadItem>() }
            .toMutableMap()
        private var otpItems: List<OtpMessageItem> = emptyList()
        private val viewTypeOtp = 0
        private val viewTypeCategory = 1
        private var selectionMode: Boolean = false
        private var selectedThreadIds: Set<Long> = emptySet()
        private var selectedOtpIds: Set<Long> = emptySet()

        private inner class CategoryPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val recycler: RecyclerView = itemView.findViewById(R.id.category_recycler)
            private val adapter = ThreadAdapter(emptyList(), onThreadClick, onThreadAvatarClick, onThreadAvatarLongPress)
            private val baseBottomPadding = itemView.resources.getDimensionPixelSize(R.dimen.thread_list_bottom_padding)

            init {
                recycler.layoutManager = LinearLayoutManager(itemView.context)
                recycler.adapter = adapter
                recycler.clipToPadding = false
                ViewCompat.setOnApplyWindowInsetsListener(recycler) { view, insets ->
                    val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        baseBottomPadding + systemInsets.bottom
                    )
                    insets
                }
                ViewCompat.requestApplyInsets(recycler)
            }

            fun bind(category: MessageCategory) {
                adapter.updateItems(itemsByCategory[category] ?: emptyList())
                adapter.updateSelectionState(selectionMode, selectedThreadIds)
            }
        }

        private inner class OtpPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val recycler: RecyclerView = itemView.findViewById(R.id.otp_recycler)
            private val adapter = OtpMessageAdapter(emptyList(), onOtpClick, onOtpAvatarClick, onOtpAvatarLongPress)
            private val baseBottomPadding = itemView.resources.getDimensionPixelSize(R.dimen.thread_list_bottom_padding)

            init {
                recycler.layoutManager = LinearLayoutManager(itemView.context)
                recycler.adapter = adapter
                recycler.clipToPadding = false
                ViewCompat.setOnApplyWindowInsetsListener(recycler) { view, insets ->
                    val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        baseBottomPadding + systemInsets.bottom
                    )
                    insets
                }
                ViewCompat.requestApplyInsets(recycler)
            }

            fun bind(items: List<OtpMessageItem>) {
                adapter.updateItems(items)
                adapter.updateSelectionState(selectionMode, selectedOtpIds)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (pages[position]) {
                is InboxPage.Otp -> viewTypeOtp
                is InboxPage.CategoryPage -> viewTypeCategory
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == viewTypeOtp) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.page_otp_list, parent, false)
                OtpPageViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.page_thread_list, parent, false)
                CategoryPageViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val page = pages[position]) {
                is InboxPage.Otp -> (holder as OtpPageViewHolder).bind(otpItems)
                is InboxPage.CategoryPage -> (holder as CategoryPageViewHolder).bind(page.category)
            }
        }

        override fun getItemCount(): Int = pages.size

        fun updateAll(otp: List<OtpMessageItem>, grouped: Map<MessageCategory, List<ThreadItem>>) {
            otpItems = otp
            grouped.forEach { (category, items) ->
                if (itemsByCategory.containsKey(category)) {
                    itemsByCategory[category] = items
                }
            }
            notifyDataSetChanged()
        }

        fun updateSelectionState(selectionEnabled: Boolean, threadIds: Set<Long>, otpIds: Set<Long>) {
            val threadCopy = threadIds.toSet()
            val otpCopy = otpIds.toSet()
            val changed = selectionMode != selectionEnabled || selectedThreadIds != threadCopy || selectedOtpIds != otpCopy
            selectionMode = selectionEnabled
            selectedThreadIds = threadCopy
            selectedOtpIds = otpCopy
            if (changed) notifyDataSetChanged()
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

        cursor?.use { c ->
            val idxThread = c.getColumnIndex("thread_id")
            val idxAddress = c.getColumnIndex("address")
            val idxDate = c.getColumnIndex("date")
            val idxBody = c.getColumnIndex("body")
            val idxRead = c.getColumnIndex("read")

            while (c.moveToNext()) {
                val threadId = if (idxThread >= 0) c.getLong(idxThread) else -1L
                if (!map.containsKey(threadId)) {
                    val address = if (idxAddress >= 0) c.getString(idxAddress) ?: "Unknown" else "Unknown"
                    val date = if (idxDate >= 0) c.getLong(idxDate) else 0L
                    val body = if (idxBody >= 0) c.getString(idxBody) ?: "" else ""
                    
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
            if (unread > 0) item.copy(unreadCount = unread) else item
        }
    }

    private fun loadOtpMessages(): List<OtpMessageItem> {
        val uri: Uri = "content://sms".toUri()
    val projection = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read")
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
                        results.add(
                            OtpMessageItem(
                                messageId = messageId,
                                threadId = threadId,
                                address = address,
                                body = body,
                                date = date,
                                otpCode = otpCode,
                                isRead = isRead
                            )
                        )
                    }
                }
            }
        }

        return results
    }

    // Helper: digits only
    private fun digitsOnly(s: String): String = s.filter { it.isDigit() }

    private fun extractOtpFromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        if (!otpKeywordPattern.containsMatchIn(body)) return null
        return otpRegex.find(body)?.value
    }

    /**
     * Robust contact lookup. Returns Pair(displayName?, photoUri?). Strategies used:
     * 1) PhoneLookup.CONTENT_FILTER_URI with multiple candidate strings (raw, normalized, E.164, +normalized, last10/9/7)
     * 2) Quick SQL suffix queries (LIKE) for last 10/9/7 digits
     * 3) Scan Phone table and use libphonenumber.PhoneNumberUtil.isNumberMatch on parsed numbers
     * 4) Fallback: compare last 10/9/7 digits
     */
    private fun lookupContactForAddress(rawAddress: String): Pair<String?, String?> {
        if (rawAddress.isBlank()) return Pair(null, null)

        try {
            val phoneUtil = PhoneNumberUtil.getInstance()
            val defaultRegion = Locale.getDefault().country.ifEmpty { "US" }
            val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(rawAddress).ifEmpty { rawAddress.replace(Regex("\\s+"), "") }
            val digits = digitsOnly(normalized)

            val tryValues = LinkedHashMap<String, Unit>()
            tryValues[rawAddress] = Unit
            if (normalized.isNotBlank()) tryValues[normalized] = Unit
            // include E.164 candidate when possible
            try {
                val parsed = phoneUtil.parse(rawAddress, defaultRegion)
                val e164 = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                if (!e164.isNullOrEmpty()) tryValues[e164] = Unit
            } catch (_: Exception) {
            }
            if (!normalized.startsWith("+")) tryValues["+" + normalized] = Unit
            if (digits.length >= 10) {
                val last10 = digits.takeLast(10)
                tryValues[last10] = Unit
                tryValues["+" + last10] = Unit
                tryValues["0" + last10] = Unit
            }
            if (digits.length >= 9) tryValues[digits.takeLast(9)] = Unit
            if (digits.length >= 7) tryValues[digits.takeLast(7)] = Unit

            Log.d("ContactLookup", "lookupContactForAddress: raw=$rawAddress normalized=$normalized tryValues=${tryValues.keys}")

            // 1) PhoneLookup quick test
            for (valToTry in tryValues.keys) {
                try {
                    val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(valToTry))
                    val proj = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI, ContactsContract.PhoneLookup._ID)
                    val cur = contentResolver.query(lookupUri, proj, null, null, null)
                    cur?.use { c ->
                        if (c.moveToFirst()) {
                            val idxName = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                            val idxPhoto = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                            val idxId = c.getColumnIndex(ContactsContract.PhoneLookup._ID)
                            val name = if (idxName >= 0) c.getString(idxName) else null
                            var photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                            val contactId = if (idxId >= 0) c.getLong(idxId) else null
                            if (photo.isNullOrEmpty() && contactId != null) {
                                try {
                                    val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                                    val p = arrayOf(ContactsContract.Contacts.PHOTO_URI)
                                    val cur2 = contentResolver.query(contactUri, p, null, null, null)
                                    cur2?.use { c2 ->
                                        if (c2.moveToFirst()) {
                                            val idxP = c2.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                            photo = if (idxP >= 0) c2.getString(idxP) else photo
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                            Log.d("ContactLookup", "PhoneLookup hit for '$valToTry' -> name=$name photo=${photo != null}")
                            return Pair(name, photo)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "phone lookup failed for $valToTry: ${e.message}")
                }
            }

            // 2) Quick suffix-query attempts using SQL LIKE on phone number for last 10/9/7 digits
            val suffixLens = listOf(10, 9, 7)
            for (len in suffixLens) {
                if (digits.length >= len) {
                    val suffix = digits.takeLast(len)
                    try {
                        val sel = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                        val args = arrayOf("%" + suffix)
                        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.PHOTO_URI, ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val cur = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, sel, args, null)
                        cur?.use { c ->
                            if (c.moveToFirst()) {
                                val idxName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                                val idxPhoto = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                                val idxContactId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                                val name = if (idxName >= 0) c.getString(idxName) else null
                                var photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                                val contactId = if (idxContactId >= 0) c.getLong(idxContactId) else null
                                if (photo.isNullOrEmpty() && contactId != null) {
                                    try {
                                        val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                                        val p = arrayOf(ContactsContract.Contacts.PHOTO_URI)
                                        val cur2 = contentResolver.query(contactUri, p, null, null, null)
                                        cur2?.use { c2 ->
                                            if (c2.moveToFirst()) {
                                                val idxP = c2.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                                photo = if (idxP >= 0) c2.getString(idxP) else photo
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                                Log.d("ContactLookup", "Suffix-query hit for last $len digits '$suffix' -> name=$name photo=${photo != null}")
                                return Pair(name, photo)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MainActivity", "suffix query failed for $suffix: ${e.message}")
                    }
                }
            }

            // 3) Parse incoming number if possible
            var parsedIncoming: com.google.i18n.phonenumbers.Phonenumber.PhoneNumber? = null
            try {
                parsedIncoming = phoneUtil.parse(rawAddress, defaultRegion)
            } catch (e: NumberParseException) {
                // ignore
            }

            // 4) Scan phone table and compare
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cursor = contentResolver.query(uri, projection, null, null, null)
            val searchDigits = digitsOnly(normalized)
            cursor?.use { c ->
                val idxNumber = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxName = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val idxPhoto = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val idxContactId = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                while (c.moveToNext()) {
                    val phone = if (idxNumber >= 0) c.getString(idxNumber) else null
                    if (!phone.isNullOrEmpty()) {
                        try {
                            if (parsedIncoming != null) {
                                try {
                                    val parsedStored = phoneUtil.parse(phone, defaultRegion)
                                    val match = phoneUtil.isNumberMatch(parsedIncoming, parsedStored)
                                    if (match == PhoneNumberUtil.MatchType.EXACT_MATCH || match == PhoneNumberUtil.MatchType.NSN_MATCH || match == PhoneNumberUtil.MatchType.SHORT_NSN_MATCH) {
                                        val name = if (idxName >= 0) c.getString(idxName) else null
                                        var photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                                        val contactId = if (idxContactId >= 0) c.getLong(idxContactId) else null
                                        if (photo.isNullOrEmpty() && contactId != null) {
                                            try {
                                                val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                                                val p = arrayOf(ContactsContract.Contacts.PHOTO_URI)
                                                val cur2 = contentResolver.query(contactUri, p, null, null, null)
                                                cur2?.use { c2 ->
                                                    if (c2.moveToFirst()) {
                                                        val idxP = c2.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                                        photo = if (idxP >= 0) c2.getString(idxP) else photo
                                                    }
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                        Log.d("ContactLookup", "Parsed match for incoming '$rawAddress' -> name=$name photo=${photo != null}")
                                        return Pair(name, photo)
                                    }
                                } catch (_: Exception) {
                                    // fallback
                                }
                            }

                            // string-based matching using libphonenumber
                            val match1 = phoneUtil.isNumberMatch(rawAddress, phone)
                            val match2 = phoneUtil.isNumberMatch(normalized, phone)
                            val match3 = if (!normalized.startsWith("+")) phoneUtil.isNumberMatch("+" + normalized, phone) else PhoneNumberUtil.MatchType.NOT_A_NUMBER
                            if (match1 == PhoneNumberUtil.MatchType.EXACT_MATCH || match1 == PhoneNumberUtil.MatchType.NSN_MATCH || match1 == PhoneNumberUtil.MatchType.SHORT_NSN_MATCH
                                || match2 == PhoneNumberUtil.MatchType.EXACT_MATCH || match2 == PhoneNumberUtil.MatchType.NSN_MATCH || match2 == PhoneNumberUtil.MatchType.SHORT_NSN_MATCH
                                || match3 == PhoneNumberUtil.MatchType.EXACT_MATCH || match3 == PhoneNumberUtil.MatchType.NSN_MATCH || match3 == PhoneNumberUtil.MatchType.SHORT_NSN_MATCH) {
                                val name = if (idxName >= 0) c.getString(idxName) else null
                                var photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                                val contactId = if (idxContactId >= 0) c.getLong(idxContactId) else null
                                if (photo.isNullOrEmpty() && contactId != null) {
                                    try {
                                        val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                                        val p = arrayOf(ContactsContract.Contacts.PHOTO_URI)
                                        val cur2 = contentResolver.query(contactUri, p, null, null, null)
                                        cur2?.use { c2 ->
                                            if (c2.moveToFirst()) {
                                                val idxP = c2.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                                photo = if (idxP >= 0) c2.getString(idxP) else photo
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                                Log.d("ContactLookup", "String match for '$rawAddress' -> name=$name photo=${photo != null}")
                                return Pair(name, photo)
                            }

                            // suffix match
                            val phoneDigits = digitsOnly(phone)
                            val suffixLens = listOf(10, 9, 7)
                            for (len in suffixLens) {
                                if (phoneDigits.length >= len && searchDigits.length >= len) {
                                    if (phoneDigits.takeLast(len) == searchDigits.takeLast(len)) {
                                        val name = if (idxName >= 0) c.getString(idxName) else null
                                        var photo = if (idxPhoto >= 0) c.getString(idxPhoto) else null
                                        val contactId = if (idxContactId >= 0) c.getLong(idxContactId) else null
                                        if (photo.isNullOrEmpty() && contactId != null) {
                                            try {
                                                val contactUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                                                val p = arrayOf(ContactsContract.Contacts.PHOTO_URI)
                                                val cur2 = contentResolver.query(contactUri, p, null, null, null)
                                                cur2?.use { c2 ->
                                                    if (c2.moveToFirst()) {
                                                        val idxP = c2.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                                                        photo = if (idxP >= 0) c2.getString(idxP) else photo
                                                    }
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                        Log.d("ContactLookup", "Suffix match for '$rawAddress' -> name=$name photo=${photo != null} len=$len")
                                        return Pair(name, photo)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore and continue
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "contact lookup failed: ${e.message}")
        }
        Log.d("ContactLookup", "No contact found for '$rawAddress'")
        return Pair(null, null)
    }
}

