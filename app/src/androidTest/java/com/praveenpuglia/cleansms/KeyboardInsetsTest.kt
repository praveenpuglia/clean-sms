package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import android.view.View
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageTestTags
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inputs (and the controls next to them) must stay above the on-screen keyboard. Edge-to-edge
 * (targetSdk 35+) means the window no longer resizes for the IME; screens must pad for it.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardInsetsTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var previousShowImeWithHardKeyboard = "0"

    @Before
    fun showSoftKeyboardEvenWithHardwareKeyboard() {
        // Emulators often report a hardware keyboard, which hides the on-screen one; real phones don't.
        previousShowImeWithHardKeyboard = shell("settings get secure show_ime_with_hard_keyboard").trim().ifEmpty { "0" }
        shell("settings put secure show_ime_with_hard_keyboard 1")
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        AppSettings.setOnboardingCompleted(context)
    }

    @After
    fun restore() {
        shell("settings put secure show_ime_with_hard_keyboard $previousShowImeWithHardKeyboard")
    }

    @Test
    fun newMessageRecipientBarAndBodyStayAboveKeyboard() {
        ActivityScenario.launch<Activity>(Intent(context, NewMessageActivity::class.java)).use { scenario ->
            composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT).performClick()
            val imeTop = awaitKeyboardTop(scenario)
            assertAbove(imeTop, "recipient field", composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT))
            assertAbove(imeTop, "send button", composeRule.onNodeWithTag(NewMessageTestTags.SEND))

            composeRule.onNodeWithTag(NewMessageTestTags.BODY).performClick()
            assertAbove(awaitKeyboardTop(scenario), "message body", composeRule.onNodeWithTag(NewMessageTestTags.BODY))
        }
    }

    @Test
    fun threadComposerStaysAboveKeyboard() {
        val intent = ThreadDetailActivity.intent(context, Long.MAX_VALUE, "+15550100088", null, null, null, MessageCategory.PERSONAL)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER_INPUT).performClick()
            val imeTop = awaitKeyboardTop(scenario)
            assertAbove(imeTop, "composer input", composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER_INPUT))
            assertAbove(imeTop, "send button", composeRule.onNodeWithTag(ThreadDetailTestTags.SEND))
        }
    }

    @Test
    fun latestMessageStaysVisibleWhenKeyboardOpensInALongThread() {
        val address = "+15550100089"
        val now = System.currentTimeMillis()
        repeat(20) { i ->
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, "Message number $i in a long conversation")
                put(Telephony.Sms.DATE, now - (20 - i) * 60_000L)
                put(Telephony.Sms.READ, 1)
            })
        }
        try {
            val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(address))
            val latestId = context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), "${Telephony.Sms.DATE} DESC")!!.use { it.moveToFirst(); it.getLong(0) }
            val intent = ThreadDetailActivity.intent(context, threadId, address, null, null, null, MessageCategory.PERSONAL)
            ActivityScenario.launch<Activity>(intent).use { scenario ->
                composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(ThreadDetailTestTags.message(latestId)).fetchSemanticsNodes().isNotEmpty() }
                composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER_INPUT).performClick()
                val imeTop = awaitKeyboardTop(scenario)
                assertAbove(imeTop, "latest message", composeRule.onNodeWithTag(ThreadDetailTestTags.message(latestId)))
            }
        } finally {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(address))
        }
    }

    @Test
    fun inboxSearchResultsEndAboveKeyboard() {
        ActivityScenario.launch<Activity>(Intent(context, MainActivity::class.java)).use { scenario ->
            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH).performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH_INPUT).performClick()
            val imeTop = awaitKeyboardTop(scenario)
            assertAbove(imeTop, "search field", composeRule.onNodeWithTag(MainInboxTestTags.SEARCH_INPUT))
            assertAbove(imeTop, "search results", composeRule.onNodeWithTag(MainInboxTestTags.SEARCH_RESULTS))
        }
    }

    /** Keyboard top edge in dp from the top of the Compose root (edge-to-edge: the whole window). */
    private fun awaitKeyboardTop(scenario: ActivityScenario<Activity>): Float {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var top = Float.NaN
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                val insets = ViewCompat.getRootWindowInsets(root)
                val ime = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true && ime > 0) {
                    top = (root.height - ime) / activity.resources.displayMetrics.density
                }
            }
            if (!top.isNaN()) {
                composeRule.waitForIdle() // let the layout settle after the IME animation
                SystemClock.sleep(500)
                composeRule.waitForIdle()
                return top
            }
            SystemClock.sleep(100)
        }
        error("on-screen keyboard never appeared")
    }

    private fun assertAbove(imeTop: Float, what: String, node: SemanticsNodeInteraction) {
        val bottom = node.getBoundsInRoot().bottom.value
        assertTrue("$what bottom=$bottom dp is under the keyboard top=$imeTop dp", bottom <= imeTop + 1f)
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().decodeToString() }
    }
}
