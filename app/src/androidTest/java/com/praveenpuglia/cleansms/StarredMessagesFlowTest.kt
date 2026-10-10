package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Starring from a message's menu marks the bubble and lists the message in the inbox's Starred tab. */
@RunWith(AndroidJUnit4::class)
class StarredMessagesFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var messageId = -1L

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        AppSettings.setOnboardingCompleted(context)
        messageId = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, ADDRESS)
            put(Telephony.Sms.BODY, "Your locker number is 214, keep this safe")
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
        })!!.lastPathSegment!!.toLong()
    }

    @After
    fun cleanUp() {
        if (messageId in StarredMessages.ids(context)) StarredMessages.toggle(context, messageId)
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(ADDRESS))
    }

    @Test
    fun starringShowsOnTheBubbleAndInTheStarredTabUntilUnstarred() {
        thread().use {
            toggleStar()
            composeRule.onNodeWithTag(ThreadDetailTestTags.starred(messageId)).assertExists()
            assertTrue(messageId in StarredMessages.ids(context))
        }

        ActivityScenario.launch<Activity>(Intent(context, MainActivity::class.java)).use {
            composeRule.onNodeWithText(context.getString(R.string.tab_starred)).performScrollTo().performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(MainInboxTestTags.message(messageId)).fetchSemanticsNodes().isNotEmpty() }
        }

        thread().use {
            toggleStar()
            composeRule.onNodeWithTag(ThreadDetailTestTags.starred(messageId)).assertDoesNotExist()
            assertFalse(messageId in StarredMessages.ids(context))
        }
    }

    private fun thread(): ActivityScenario<Activity> {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(ADDRESS))
        return ActivityScenario.launch(ThreadDetailActivity.intent(context, threadId, ADDRESS, null, null, null, MessageCategory.UNKNOWN))
    }

    private fun toggleStar() {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(ThreadDetailTestTags.messageMenu(messageId)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(ThreadDetailTestTags.messageMenu(messageId)).performClick()
        composeRule.onNodeWithTag(ThreadDetailTestTags.STAR).performClick()
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val ADDRESS = "+919000022233"
    }
}
