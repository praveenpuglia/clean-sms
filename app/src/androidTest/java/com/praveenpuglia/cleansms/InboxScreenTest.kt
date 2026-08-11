package com.praveenpuglia.cleansms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.praveenpuglia.cleansms.ui.inbox.InboxPage
import com.praveenpuglia.cleansms.ui.inbox.InboxScreen
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allOtpAndCategoryPagesRenderTheirOwnRowsAndActions() {
        val pages = listOf(InboxPage.All, InboxPage.Otp, InboxPage.CategoryPage(MessageCategory.PERSONAL))
        val thread = ThreadItem(10, "+919876543210", 3, "Call me", contactName = "Mom", category = MessageCategory.PERSONAL, unreadCount = 2, hasSpam = true)
        val otp = OtpMessageItem(20, 20, "VK-GOOGLE-T", "Your OTP is 892341", 2, "892341", isRead = false)
        val all = SearchResultItem(30, 30, "VM-BANK-T", null, "Rs.500 credited", 1, null, null, MessageCategory.TRANSACTIONAL, isUnread = true)
        var selectedPage by mutableIntStateOf(0)
        var copied = ""
        var openedStats = false
        composeRule.setContent {
            CleanSmsTheme {
                InboxScreen(
                    pages = pages,
                    selectedPageIndex = selectedPage,
                    scrollToTopRequest = 0,
                    allThreads = listOf(thread),
                    otpMessages = listOf(otp),
                    allItems = listOf(all),
                    searchResults = emptyList(),
                    searchMode = false,
                    searchQuery = "",
                    unreadOnly = false,
                    selectionMode = false,
                    selectedThreadIds = emptySet(),
                    selectedMessageIds = emptySet(),
                    promoMuted = false,
                    permissionRequired = false,
                    showDeleteDialog = false,
                    onPageSelected = { selectedPage = it },
                    onSearchModeChange = {},
                    onSearchQueryChange = {},
                    onUnreadOnlyChange = {},
                    onOpenStats = { openedStats = true },
                    onOpenSettings = {},
                    onNewMessage = {},
                    onThreadClick = {},
                    onThreadAvatarClick = {},
                    onThreadLongClick = {},
                    onOtpClick = {},
                    onOtpAvatarClick = {},
                    onOtpLongClick = {},
                    onCopyOtp = { copied = it },
                    onMessageClick = {},
                    onSelectAll = {},
                    onMarkAsRead = {},
                    onDeleteRequest = {},
                    onDeleteConfirm = {},
                    onDeleteDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(MainInboxTestTags.tab(0)).assertIsSelected()
        composeRule.onNodeWithTag(MainInboxTestTags.message(30)).assertIsDisplayed()
        composeRule.onNodeWithTag(MainInboxTestTags.tabUnread(0), useUnmergedTree = true).assertExists()

        composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).assertIsSelected()
        composeRule.onNodeWithTag(MainInboxTestTags.otpCode(20)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("892341", copied) }

        composeRule.onNodeWithTag(MainInboxTestTags.tab(2)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainInboxTestTags.tab(2)).assertIsSelected()
        composeRule.onNodeWithTag(MainInboxTestTags.thread(10)).assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Contains spam").assertIsDisplayed()

        composeRule.onNodeWithTag(MainInboxTestTags.MORE).performClick()
        composeRule.onNodeWithTag(MainInboxTestTags.STATS).performClick()
        composeRule.runOnIdle { assertTrue(openedStats) }
    }
}
