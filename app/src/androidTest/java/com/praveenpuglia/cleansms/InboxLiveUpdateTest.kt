package com.praveenpuglia.cleansms

import android.content.ContentValues
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Inbox reacts to provider changes while it is open, and destructive actions really apply. */
@RunWith(AndroidJUnit4::class)
class InboxLiveUpdateTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        context.getSharedPreferences("CleanSmsPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_completed", true).commit()
        AppSettings.setAllTabEnabled(context, false)
        AppSettings.setDefaultTab(context, AppSettings.DefaultTab.OTP)
        cleanUp()
    }

    @After
    fun cleanUp() {
        listOf(LIVE_SENDER, DELETE_SENDER, THREAD_SENDER).forEach {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(it))
        }
    }

    @Test
    fun otpArrivingWhileInboxIsOpenAppearsWithoutReopening() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            SmsDeliverReceiver.deliver(context, LIVE_SENDER, "Your OTP is 650213 for login")
            val id = idFor(LIVE_SENDER)
            waitForTag(MainInboxTestTags.otpCode(id))
        }
    }

    @Test
    fun deletingASelectedConversationRemovesItFromProviderAndList() {
        context.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, DELETE_SENDER)
                put(Telephony.Sms.BODY, "Delete me please")
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
            },
        )
        val threadId = threadIdFor(DELETE_SENDER)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Personal").performClick()
            waitForTag(MainInboxTestTags.thread(threadId))
            composeRule.onNodeWithTag(MainInboxTestTags.thread(threadId)).performSemanticsAction(SemanticsActions.OnLongClick)
            composeRule.onNodeWithContentDescription("Delete selected conversations or messages").performClick()
            composeRule.onNodeWithText("Delete").performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(MainInboxTestTags.thread(threadId)).fetchSemanticsNodes().isEmpty()
            }
            assertEquals(0, countFor(DELETE_SENDER))
        }
    }

    @Test
    fun burstOfIncomingOtpsEndsOnTheNewest() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            repeat(5) { SmsDeliverReceiver.deliver(context, LIVE_SENDER, "Your OTP is 70000$it for login") }
            // Coalesced reloads end on the newest state, and the list follows new rows to the top.
            val newest = ids(LIVE_SENDER).also { assertEquals(5, it.size) }.max()
            waitForTag(MainInboxTestTags.otpCode(newest))
        }
    }

    @Test
    fun readerScrolledDownIsNotYankedToTopByANewMessage() {
        shell("am broadcast -n ${context.packageName}/.DebugSeedReceiver -a com.praveenpuglia.cleansms.DEBUG_SEED")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (otpIdsNewestFirst().size <= 12 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(200)
        ActivityScenario.launch(MainActivity::class.java).use {
            val existing = otpIdsNewestFirst()
            check(existing.size > 12) { "needs a scrollable OTP list (seeded inbox)" }
            val readingId = existing[10]
            waitForTag(MainInboxTestTags.otp(existing.first()))
            otpList(existing.first()).performScrollToKey(readingId)
            waitForTag(MainInboxTestTags.otp(readingId))

            SmsDeliverReceiver.deliver(context, LIVE_SENDER, "Your OTP is 650213 for login")
            val newId = idFor(LIVE_SENDER)
            SystemClock.sleep(1_500) // observer debounce + reload
            composeRule.waitForIdle()
            assertTrue(composeRule.onAllNodesWithTag(MainInboxTestTags.otp(readingId)).fetchSemanticsNodes().isNotEmpty())
            assertTrue(composeRule.onAllNodesWithTag(MainInboxTestTags.otp(newId)).fetchSemanticsNodes().isEmpty())
        }
    }

    private fun otpList(anyRowId: Long) =
        // Innermost scrollable holding the row is the OTP list (the pager also matches).
        composeRule.onAllNodes(hasScrollToKeyAction() and hasAnyDescendant(hasTestTag(MainInboxTestTags.otp(anyRowId))))
            .run { get(fetchSemanticsNodes().lastIndex) }

    private fun otpIdsNewestFirst(): List<Long> =
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY), null, null, "${Telephony.Sms.DATE} DESC")!!
            .use { c -> buildList { while (c.moveToNext()) if (CategoryClassifier.extractHighPrecisionOtp(c.getString(1).orEmpty()) != null) add(c.getLong(0)) } }

    @Test
    fun messageArrivingInAnOpenThreadAppears() {
        insertInbox(THREAD_SENDER, "First message")
        val threadId = threadIdFor(THREAD_SENDER)
        ActivityScenario.launch<ThreadDetailActivity>(threadIntent(threadId)).use {
            waitForTag(ThreadDetailTestTags.message(idFor(THREAD_SENDER)))
            SmsDeliverReceiver.deliver(context, THREAD_SENDER, "Second message")
            val second = ids(THREAD_SENDER).first { it != idFor(THREAD_SENDER, "First message") }
            waitForTag(ThreadDetailTestTags.message(second))
        }
    }

    @Test
    fun doubleTappingSendRecordsOneMessage() {
        shell("pm grant ${context.packageName} android.permission.SEND_SMS")
        insertInbox(THREAD_SENDER, "Ping")
        ActivityScenario.launch<ThreadDetailActivity>(threadIntent(threadIdFor(THREAD_SENDER))).use {
            composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER_INPUT).performTextReplacement("Only once")
            composeRule.onNodeWithTag(ThreadDetailTestTags.SEND).performClick()
            composeRule.onNodeWithTag(ThreadDetailTestTags.SEND).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { sentCount(THREAD_SENDER, "Only once") > 0 }
            SystemClock.sleep(1_000)
            assertEquals(1, sentCount(THREAD_SENDER, "Only once"))
        }
    }

    @Test
    fun selectionStartedRightAfterMarkAsReadIsNotWipedWhenTheWriteFinishes() {
        listOf(LIVE_SENDER, DELETE_SENDER).forEach { insertInbox(it, "Unread for selection", read = 0) }
        val first = threadIdFor(LIVE_SENDER)
        val second = threadIdFor(DELETE_SENDER)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Personal").performClick()
            waitForTag(MainInboxTestTags.thread(second))
            composeRule.onNodeWithTag(MainInboxTestTags.thread(first)).performSemanticsAction(SemanticsActions.OnLongClick)
            composeRule.onNodeWithContentDescription("Mark selected conversations or messages as read").performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.thread(second)).performSemanticsAction(SemanticsActions.OnLongClick)

            SystemClock.sleep(1_500) // let the mark-as-read write and reload complete
            composeRule.waitForIdle()
            assertEquals(1, composeRule.onAllNodesWithText("1 selected").fetchSemanticsNodes().size)
        }
    }

    private fun threadIntent(threadId: Long) = ThreadDetailActivity.intent(
        context, threadId, THREAD_SENDER, null, null, null, MessageCategory.PERSONAL,
    )

    private fun insertInbox(address: String, body: String, read: Int = 1) {
        context.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, read)
            },
        )
    }

    private fun ids(address: String): List<Long> =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), null)!!
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    private fun idFor(address: String, body: String): Long =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?", arrayOf(address, body), null)!!
            .use { check(it.moveToFirst()); it.getLong(0) }

    private fun sentCount(address: String, body: String): Int =
        context.contentResolver.query(Telephony.Sms.Sent.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?", arrayOf(address, body), null)!!
            .use { it.count }

    private fun waitForTag(tag: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun idFor(address: String): Long = query(address, Telephony.Sms._ID)
    private fun threadIdFor(address: String): Long = query(address, Telephony.Sms.THREAD_ID)

    private fun query(address: String, column: String): Long =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(column), "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), null)!!
            .use { check(it.moveToFirst()) { "no row for test sender" }; it.getLong(0) }

    private fun countFor(address: String): Int =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), null)!!
            .use { it.count }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val LIVE_SENDER = "+15550100031"
        const val DELETE_SENDER = "+15550100032"
        const val THREAD_SENDER = "+15550100033"
    }
}
