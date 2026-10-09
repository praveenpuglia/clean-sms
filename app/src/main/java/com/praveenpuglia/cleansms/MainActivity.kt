package com.praveenpuglia.cleansms

import android.Manifest
import android.annotation.SuppressLint
import androidx.core.net.toUri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Telephony
import android.app.role.RoleManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.provider.ContactsContract
import com.praveenpuglia.cleansms.ui.inbox.InboxScreen
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingScreen
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingUiState
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val observerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val inbox: InboxViewModel by viewModels()
    private val reloadFromObserver = Runnable { inbox.reload() }
    // Reload while visible when SMS change (incoming, other apps, our own writes); debounced.
    private val smsObserver = object : ContentObserver(observerHandler) {
        override fun onChange(selfChange: Boolean) {
            observerHandler.removeCallbacks(reloadFromObserver)
            observerHandler.postDelayed(reloadFromObserver, OBSERVER_DEBOUNCE_MS)
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

    private var onboardingUiState by mutableStateOf(OnboardingUiState())
    private var lastAppliedAllTabEnabled: Boolean = false
    private var lastAppliedFontFamily: AppSettings.FontFamily = AppSettings.FontFamily.SANS_SERIF
    private var permissionRequired by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(true)
    private var promoMuted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        AppCompatDelegate.setDefaultNightMode(AppSettings.getThemeMode(this))
        super.onCreate(savedInstanceState)

        lastAppliedAllTabEnabled = AppSettings.getAllTabEnabled(this)
        lastAppliedFontFamily = AppSettings.getFontFamily(this)
        inbox.configure(lastAppliedAllTabEnabled, AppSettings.getDefaultTab(this))

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
                        pages = inbox.pages,
                        selectedPageIndex = inbox.selectedPageIndex,
                        scrollToTopRequest = inbox.scrollToTopRequest,
                        allThreads = inbox.allThreads,
                        otpMessages = inbox.otpMessages,
                        allItems = inbox.allTabItems,
                        searchResults = inbox.searchResults,
                        searchMode = inbox.searchMode,
                        searchQuery = inbox.searchQuery,
                        unreadOnly = inbox.unreadOnly,
                        selectionMode = inbox.selectionMode,
                        selectedThreadIds = inbox.selectedThreadIds,
                        selectedMessageIds = inbox.selectedMessageIds,
                        promoMuted = promoMuted,
                        permissionRequired = permissionRequired,
                        showDeleteDialog = inbox.showDeleteDialog,
                        onPageSelected = inbox::selectPage,
                        onSearchModeChange = { enabled -> if (enabled) inbox.enterSearchMode() else inbox.exitSearchMode() },
                        onSearchQueryChange = inbox::updateSearchQuery,
                        onUnreadOnlyChange = inbox::setUnreadFilter,
                        onOpenStats = { startActivity(Intent(this, StatsActivity::class.java)) },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                        onNewMessage = { startActivity(Intent(this, NewMessageActivity::class.java)) },
                        onThreadClick = ::handleThreadClick,
                        onThreadAvatarClick = ::handleThreadAvatarClick,
                        onThreadLongClick = inbox::startThreadSelection,
                        onOtpClick = ::handleOtpClick,
                        onOtpAvatarClick = ::handleOtpAvatarClick,
                        onOtpLongClick = inbox::startOtpSelection,
                        onCopyOtp = ::copyOtp,
                        onMessageClick = ::handleSearchResultClick,
                        onSelectAll = inbox::toggleSelectAll,
                        onMarkAsRead = inbox::markSelectionAsRead,
                        onDeleteRequest = inbox::confirmDeleteSelection,
                        onDeleteConfirm = inbox::performDeletion,
                        onDeleteDismiss = { inbox.showDeleteDialog = false },
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    inbox.searchMode -> inbox.exitSearchMode()
                    inbox.selectionMode -> inbox.exitSelectionMode()
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
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        // Re-check after potential default change
        setupDefaultSmsUi()
        // If the All-tab preference changed in Settings, simplest path is to rebuild the activity.
        if (AppSettings.getAllTabEnabled(this) != lastAppliedAllTabEnabled ||
            AppSettings.getFontFamily(this) != lastAppliedFontFamily) {
            recreate()
            return
        }
        if (hasReadPermission()) {
            inbox.reload()
        }
        promoMuted = !AppSettings.getPromoNotificationsEnabled(this)
    }

    override fun onPause() {
        observerHandler.removeCallbacks(reloadFromObserver)
        contentResolver.unregisterContentObserver(smsObserver)
        super.onPause()
    }

    private fun setupDefaultSmsUi() {
        val isDefault = DefaultSmsHelper.isDefaultSmsApp(this)
        val powerManager = getSystemService(PowerManager::class.java)
        val isBatteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        onboardingUiState = OnboardingUiState(isDefault, isBatteryOptimizationIgnored)
        
        val hasCompletedOnboarding = AppSettings.isOnboardingCompleted(this)
        
        Log.d("DefaultSmsUI", "isDefault=$isDefault pkg=${packageName} batteryIgnored=$isBatteryOptimizationIgnored hasCompletedOnboarding=$hasCompletedOnboarding")
        
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

    // Product decision (2026-10): keep the one-tap exemption in onboarding. Stock Android delivers
    // SMS to the default app in Doze without it (verified on API 37); revisit if Play review objects.
    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            })
        } catch (e: RuntimeException) {
            Log.w("BatteryOptimization", "Request failed: ${e.message}")
            Toast.makeText(this, R.string.toast_battery_optimization_settings, Toast.LENGTH_LONG).show()
        }
    }

    private fun completeOnboarding() {
        if (!onboardingUiState.isDefaultSmsApp || !onboardingUiState.isBatteryOptimizationIgnored) return

        AppSettings.setOnboardingCompleted(this)
        showOnboarding = false

        if (hasReadPermission()) {
            showThreadsUi()
        } else {
            showInstructionsUi()
            ActivityCompat.requestPermissions(this, requestedPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun showThreadsUi() {
        permissionRequired = false
        inbox.reload()
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
        val existingThread = inbox.allThreads.firstOrNull { it.threadId == item.threadId }
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
        if (inbox.selectionMode) {
            inbox.toggleThreadSelection(item)
        } else {
            openThreadDetail(item)
        }
    }

    private fun handleThreadAvatarClick(item: ThreadItem) {
        if (inbox.selectionMode) {
            inbox.toggleThreadSelection(item)
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
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOtpClick(item: OtpMessageItem) {
        if (inbox.selectionMode) {
            inbox.toggleOtpSelection(item)
        } else {
            openThreadDetailFromOtp(item)
        }
    }

    private fun handleOtpAvatarClick(item: OtpMessageItem) {
        if (inbox.selectionMode) {
            inbox.toggleOtpSelection(item)
            return
        }
        if (item.hasSavedContact) {
            openContactFromOtp(item)
        }
        // OTP messages are service messages - no add-to-contacts for unknown senders
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
        val existingUri = lookupUriString?.let { it.toUri() }
        if (existingUri != null) {
            launchContactIntent(existingUri)
            return
        }
        lifecycleScope.launch {
            val resolvedUri = withContext(Dispatchers.IO) {
                ContactDirectory.findLookupUri(this@MainActivity, rawAddress, contactName, contactPhotoUri)
            }
            if (resolvedUri != null) {
                launchContactIntent(resolvedUri)
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.toast_contact_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchContactIntent(contactUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, contactUri)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("MainActivity", "Failed to open contact: ${e.javaClass.simpleName}")
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




    // ========== Overflow Menu ==========
    



    private fun copyOtp(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        clipboard?.setPrimaryClip(ClipData.newPlainText("OTP", code))
        Toast.makeText(this, R.string.toast_otp_copied, Toast.LENGTH_SHORT).show()
    }


    

    
    
    
    
    private fun handleSearchResultClick(item: SearchResultItem) {
        // Find or create thread item to navigate
        val existingThread = inbox.allThreads.firstOrNull { it.threadId == item.threadId }
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

    private companion object {
        const val OBSERVER_DEBOUNCE_MS = 300L
    }
}
