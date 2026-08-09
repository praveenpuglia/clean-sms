package com.praveenpuglia.cleansms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailScreen
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun personalThreadRendersBothDirectionsComposerAndTargetHighlight() {
        var highlightFinished = false
        val messages = listOf(
            message(1, "Hello", type = 1),
            message(2, "On my way", type = 2),
        )
        composeRule.setContent {
            CleanSmsTheme {
                ThreadDetailScreen(
                    contactName = "Mom",
                    contactAddress = "+919876543210",
                    contactPhotoUri = null,
                    category = MessageCategory.PERSONAL,
                    messages = messages,
                    messageText = "Reply",
                    showComposer = true,
                    focusComposer = false,
                    selectedSimNumber = 2,
                    showSimSelector = true,
                    highlightedMessageId = 1,
                    scrollRequest = 1,
                    onBack = {},
                    onAvatarClick = {},
                    onCall = {},
                    onMessageChange = {},
                    onSimToggle = {},
                    onSend = {},
                    onHighlightFinished = { highlightFinished = true },
                )
            }
        }

        composeRule.onNodeWithTag(ThreadDetailTestTags.CONTACT_NAME).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Call").assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send using SIM 2").assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadDetailTestTags.message(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadDetailTestTags.message(2)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sent").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 3_000) { highlightFinished }
        assertTrue(highlightFinished)
    }

    @Test
    fun serviceSpamThreadHasBadgeAndNoReplyActions() {
        val body = "Airtel Warning: SPAM | Visit https://example.com now"
        composeRule.setContent {
            CleanSmsTheme {
                ThreadDetailScreen(
                    contactName = null,
                    contactAddress = "VM-SPAM-S",
                    contactPhotoUri = null,
                    category = MessageCategory.SERVICE,
                    messages = listOf(message(3, body, type = 1)),
                    messageText = "",
                    showComposer = false,
                    focusComposer = false,
                    selectedSimNumber = null,
                    showSimSelector = false,
                    highlightedMessageId = null,
                    scrollRequest = 0,
                    onBack = {},
                    onAvatarClick = {},
                    onCall = {},
                    onMessageChange = {},
                    onSimToggle = {},
                    onSend = {},
                    onHighlightFinished = {},
                )
            }
        }

        composeRule.onNodeWithText(body).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Spam warning").assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Call").assertDoesNotExist()
    }

    private fun message(id: Long, body: String, type: Int) = Message(
        id = id,
        threadId = 10,
        address = "+919876543210",
        body = body,
        date = System.currentTimeMillis(),
        type = type,
        simSlot = 1,
        status = 0,
    )
}
