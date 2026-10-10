package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Any received message can be reported to TRAI's 1909 from its menu, after confirming the exact complaint. */
@RunWith(AndroidJUnit4::class)
class TraiReportFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        shell("pm grant ${context.packageName} android.permission.SEND_SMS")
        AppSettings.setOnboardingCompleted(context)
        cleanUp()
    }

    @After
    fun cleanUp() {
        listOf(SPAMMER, OLD_SPAMMER, TraiReport.SHORT_CODE).forEach {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(it))
        }
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
    }
}
