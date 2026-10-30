package com.praveenpuglia.cleansms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
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
        val busyThread = ThreadItem(11, "+919800000000", 1, "Lots", category = MessageCategory.PERSONAL, unreadCount = 12)
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
                    allThreads = listOf(thread, busyThread),
                    otpMessages = listOf(otp),
                    allItems = listOf(all),
                    starredItems = emptyList(),
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
        // M3 badge: one digit is a circle, two digits stretch into a pill.
        composeRule.onNodeWithTag(MainInboxTestTags.unreadBadge(10), useUnmergedTree = true).getBoundsInRoot().let {
            assertEquals((it.right - it.left).value, (it.bottom - it.top).value, 0.5f)
        }
        composeRule.onNodeWithTag(MainInboxTestTags.unreadBadge(11), useUnmergedTree = true).getBoundsInRoot().let {
            assertTrue((it.right - it.left) > (it.bottom - it.top))
        }
        // Badge sits on the sender line, right after the name (above the snippet, left of the time).
        val badge = composeRule.onNodeWithTag(MainInboxTestTags.unreadBadge(10), useUnmergedTree = true).getBoundsInRoot()
        val name = composeRule.onNodeWithText("Mom", useUnmergedTree = true).getBoundsInRoot()
        val snippet = composeRule.onNodeWithText("Call me", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(badge.left >= name.right)
        assertTrue(badge.bottom <= snippet.top)
        composeRule.onNodeWithContentDescription("Contains spam").assertIsDisplayed()

        composeRule.onNodeWithTag(MainInboxTestTags.MORE).performClick()
        composeRule.onNodeWithTag(MainInboxTestTags.STATS).performClick()
        composeRule.runOnIdle { assertTrue(openedStats) }
    }

    private val otpPage = InboxPage.Otp
    private val personalPage = InboxPage.CategoryPage(MessageCategory.PERSONAL)
    private val transactionsPage = InboxPage.CategoryPage(MessageCategory.TRANSACTIONAL)

    private fun showTabs(
        onReorder: (List<InboxPage>) -> Unit = {},
        showHint: Boolean = false,
        onHintSeen: () -> Unit = {},
    ) {
        composeRule.setContent {
            CleanSmsTheme {
                InboxScreen(
                    pages = listOf(otpPage, personalPage, transactionsPage),
                    selectedPageIndex = 0, scrollToTopRequest = 0,
                    allThreads = emptyList(), otpMessages = emptyList(), allItems = emptyList(), starredItems = emptyList(),
                    searchResults = emptyList(), searchMode = false, searchQuery = "", unreadOnly = false,
                    selectionMode = false, selectedThreadIds = emptySet(), selectedMessageIds = emptySet(),
                    promoMuted = false, permissionRequired = false, showDeleteDialog = false,
                    onPageSelected = {}, onSearchModeChange = {}, onSearchQueryChange = {}, onUnreadOnlyChange = {},
                    onOpenStats = {}, onOpenSettings = {}, onNewMessage = {}, onThreadClick = {}, onThreadAvatarClick = {},
                    onThreadLongClick = {}, onOtpClick = {}, onOtpAvatarClick = {}, onOtpLongClick = {}, onCopyOtp = {},
                    onMessageClick = {}, onSelectAll = {}, onMarkAsRead = {}, onDeleteRequest = {}, onDeleteConfirm = {},
                    onDeleteDismiss = {},
                    onReorderPages = onReorder,
                    showTabReorderHint = showHint,
                    onTabReorderHintSeen = onHintSeen,
                )
            }
        }
    }

    @Test
    fun holdingATabAndDraggingItPastItsNeighbourMovesIt() {
        var reordered: List<InboxPage>? = null
        showTabs(onReorder = { reordered = it })
        val neighbourWidth = composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).getBoundsInRoot().let { it.right - it.left }

        composeRule.onNodeWithTag(MainInboxTestTags.tab(0)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // Past half of the neighbour, short of the one after it
            val step = neighbourWidth.toPx() * 0.75f / 10
            repeat(10) { moveBy(Offset(step, 0f)) }
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(personalPage, otpPage, transactionsPage), reordered) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun screenReaderUsersCanMoveATabWithAnAction() {
        var reordered: List<InboxPage>? = null
        showTabs(onReorder = { reordered = it })

        composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).performCustomAccessibilityActionWithLabel("Move right")

        composeRule.runOnIdle { assertEquals(listOf(otpPage, transactionsPage, personalPage), reordered) }
    }

    @Test
    fun theReorderTipShowsOnceAndReportsItselfSeen() {
        var seen = false
        showTabs(showHint = true, onHintSeen = { seen = true })

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(MainInboxTestTags.TAB_REORDER_HINT).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Got it").performClick()

        composeRule.waitUntil(5_000) { seen }
    }
}
