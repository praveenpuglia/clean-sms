package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageTestTags
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A message's menu: reporting any received message to TRAI's 1909 after confirming the exact complaint,
 * and the row of actions every message has (star, forward, copy, delete).
 */
@RunWith(AndroidJUnit4::class)
class MessageMenuFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        shell("dumpsys deviceidle whitelist +${context.packageName}")
        shell("pm grant ${context.packageName} android.permission.SEND_SMS")
        AppSettings.setOnboardingCompleted(context)
        cleanUp()
    }

    @After
    fun cleanUp() {
        listOf(SPAMMER, OLD_SPAMMER, LONE_SENDER, TraiReport.SHORT_CODE).forEach {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(it))
        }
        StarredMessages.prune(context)
    }

    @Test
    fun anyReceivedMessageCanBeReportedWithItsFullTextFromTheMessageMenu() {
        val received = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        // No operator label: TRAI takes complaints about any unsolicited commercial message
        val body = "Earn Rs.5,000 a day from home.\nJoin now: example.com/x"
        val id = insert(SPAMMER, body, received)
        launch(SPAMMER).use {
            openMenu(id)
            composeRule.onNodeWithTag(ThreadDetailTestTags.REPORT_SPAM).performClick()

            val complaint = TraiReport.complaint(body, SPAMMER, received)
            assertEquals("Earn Rs.5,000 a day from home. Join now: example.com/x, $SPAMMER, ", complaint.dropLast(8))
            composeRule.onNodeWithTag(ThreadDetailTestTags.REPORT_DIALOG).assertIsDisplayed()
            composeRule.onNodeWithText(complaint).assertIsDisplayed()

            // Confirming sends a real SMS: only on an emulator, never file a real complaint from a test phone.
            assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish"))
            composeRule.onNodeWithTag(ThreadDetailTestTags.REPORT_CONFIRM).performClick()
            assertEquals(listOf(complaint), awaitBodies(TraiReport.SHORT_CODE))
        }
    }

    @Test
    fun reportIsDisabledAfterSevenDaysAndAbsentOnYourOwnMessages() {
        val old = insert(OLD_SPAMMER, "Jio Alert : SPAM Old offer", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))
        val mine = insert(OLD_SPAMMER, "Stop texting me", System.currentTimeMillis(), Telephony.Sms.Sent.CONTENT_URI)
        launch(OLD_SPAMMER).use {
            openMenu(old)
            composeRule.onNodeWithTag(ThreadDetailTestTags.REPORT_SPAM).assertIsNotEnabled()
            composeRule.onNodeWithText(context.getString(R.string.thread_report_spam_expired)).assertIsDisplayed()
            Espresso.pressBack() // close the menu

            openMenu(mine)
            composeRule.onNodeWithTag(ThreadDetailTestTags.STAR).assertIsDisplayed()
            composeRule.onNodeWithTag(ThreadDetailTestTags.REPORT_SPAM).assertDoesNotExist()
        }
    }

    @Test
    fun deleteFromTheMessageMenuRemovesOnlyThatMessageAndItsStar() {
        val keep = insert(SPAMMER, "Keep me", System.currentTimeMillis() - 1_000)
        val gone = insert(SPAMMER, "Delete me", System.currentTimeMillis())
        StarredMessages.toggle(context, gone)
        launch(SPAMMER).use {
            delete(gone)
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(ThreadDetailTestTags.message(gone)).fetchSemanticsNodes().isEmpty() }
            composeRule.onNodeWithTag(ThreadDetailTestTags.message(keep)).assertIsDisplayed()
            assertEquals(listOf("Keep me"), awaitBodies(SPAMMER))
            assertFalse(gone in StarredMessages.ids(context))
        }
    }

    @Test
    fun deletingTheLastMessageDeletesTheThreadAndClosesTheScreen() {
        val only = insert(LONE_SENDER, "Only message", System.currentTimeMillis())
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(LONE_SENDER))
        launch(LONE_SENDER).use { scenario ->
            delete(only)
            composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
        val threadLeft = context.contentResolver.query(
            "content://mms-sms/conversations?simple=true".toUri(), arrayOf("_id"), "_id = ?", arrayOf(threadId.toString()), null,
        )?.use { it.count > 0 }
        assertEquals(false, threadLeft)
    }

    @Test
    fun starsOfMessagesDeletedElsewhereArePruned() {
        val kept = insert(SPAMMER, "Still here", System.currentTimeMillis())
        val goneElsewhere = insert(SPAMMER, "Deleted from the inbox", System.currentTimeMillis())
        StarredMessages.toggle(context, kept)
        StarredMessages.toggle(context, goneElsewhere)
        context.contentResolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, goneElsewhere), null, null)

        StarredMessages.prune(context)

        assertTrue(kept in StarredMessages.ids(context))
        assertFalse(goneElsewhere in StarredMessages.ids(context))
    }

    @Test
    fun forwardOpensANewMessageWithTheWholeTextWaitingForARecipient() {
        // "&" and "%" would be cut or mangled if the body travelled in a URI query
        val body = "Gate code 4412 & parking B2, map: https://maps.example/?q=a&z=17, 100% sure"
        val id = insert(SPAMMER, body, System.currentTimeMillis())
        launch(SPAMMER).use {
            openMenu(id)
            composeRule.onNodeWithTag(ThreadDetailTestTags.FORWARD).performClick()
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(NewMessageTestTags.BODY).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag(NewMessageTestTags.BODY).assertTextEquals(body)
            composeRule.waitUntil(5_000) { runCatching { composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT).assertIsFocused() }.isSuccess }
            composeRule.onNodeWithContentDescription(context.getString(R.string.settings_back)).performClick()
        }
    }

    @Test
    fun copyPutsTheMessageTextOnTheClipboard() {
        val body = "Wifi password: tulip-42"
        val id = insert(SPAMMER, body, System.currentTimeMillis())
        launch(SPAMMER).use {
            openMenu(id)
            composeRule.onNodeWithTag(ThreadDetailTestTags.COPY).performClick()
            composeRule.runOnIdle {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                assertEquals(body, clip?.getItemAt(0)?.text?.toString())
            }
        }
    }

    private fun delete(messageId: Long) {
        openMenu(messageId)
        composeRule.onNodeWithTag(ThreadDetailTestTags.DELETE).performClick()
        composeRule.onNodeWithTag(ThreadDetailTestTags.DELETE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadDetailTestTags.DELETE_CONFIRM).performClick()
    }

    private fun openMenu(messageId: Long) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(ThreadDetailTestTags.messageMenu(messageId)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(ThreadDetailTestTags.messageMenu(messageId)).performClick()
    }

    private fun launch(address: String): ActivityScenario<Activity> {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(address))
        return ActivityScenario.launch(ThreadDetailActivity.intent(context, threadId, address, null, null, null, MessageCategory.UNKNOWN))
    }

    private fun insert(address: String, body: String, date: Long, box: android.net.Uri = Telephony.Sms.Inbox.CONTENT_URI): Long =
        context.contentResolver.insert(box, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 1)
        })!!.lastPathSegment!!.toLong()

    private fun awaitBodies(address: String): List<String> {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var bodies = emptyList<String>()
        while (SystemClock.uptimeMillis() < deadline) {
            bodies = context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), null,
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.orEmpty()
            if (bodies.isNotEmpty()) break
            SystemClock.sleep(200)
        }
        return bodies
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val SPAMMER = "+919000011122"
        const val OLD_SPAMMER = "+919000011133"
        const val LONE_SENDER = "+919000011144"
    }
}
