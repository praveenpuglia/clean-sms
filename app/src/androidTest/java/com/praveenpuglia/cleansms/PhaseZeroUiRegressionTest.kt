package com.praveenpuglia.cleansms

import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import android.text.style.URLSpan
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingTestTags
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageTestTags
import com.praveenpuglia.cleansms.ui.thread.ThreadDetailTestTags
import com.praveenpuglia.cleansms.ui.stats.StatsTestTags
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseZeroUiRegressionTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val packageName = context.packageName
    private val prefs = context.getSharedPreferences("CleanSmsPrefs", Context.MODE_PRIVATE)

    @After
    fun restoreStableDeviceState() {
        ensureSmsRole()
        prefs.edit().putBoolean("onboarding_completed", true).commit()
        SettingsActivity.setThemeMode(context, SettingsActivity.THEME_DARK)
        SettingsActivity.setFontFamily(context, SettingsActivity.FontFamily.SANS_SERIF)
    }

    @Test
    fun onboardingRequiresBothSetupStepsToContinue() {
        ensureSmsRole()
        shell("dumpsys deviceidle whitelist -$packageName")
        prefs.edit().putBoolean("onboarding_completed", false).commit()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Welcome to Clean SMS").assertIsDisplayed()
            composeRule.onNodeWithTag(OnboardingTestTags.SET_DEFAULT).assertIsNotEnabled()
            composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsNotEnabled()
            composeRule.onNodeWithContentDescription("Clean SMS logo").assertIsDisplayed()
        }

        shell("dumpsys deviceidle whitelist +$packageName")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsEnabled()
            }
        } finally {
            shell("dumpsys deviceidle whitelist -$packageName")
        }
    }

    @Test
    fun settingsPersistAndDisablingAllRepairsTheDefaultTab() {
        SettingsActivity.setAllTabEnabled(context, true)
        SettingsActivity.setDefaultTab(context, SettingsActivity.DefaultTab.ALL)
        SettingsActivity.setPromoNotificationsEnabled(context, true)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            composeRule.onNodeWithTag(SettingsTestTags.ALL_TAB).assertIsOn()
            composeRule.onNodeWithTag(SettingsTestTags.DEFAULT_TAB).assertTextContains("All")
            composeRule.onNodeWithTag(SettingsTestTags.PROMO_NOTIFICATIONS).performClick()
            composeRule.onNodeWithTag(SettingsTestTags.PROMO_NOTIFICATIONS).assertIsOff()
            composeRule.onNodeWithTag(SettingsTestTags.ALL_TAB).performClick()
            composeRule.onNodeWithTag(SettingsTestTags.ALL_TAB).assertIsOff()
            composeRule.onNodeWithTag(SettingsTestTags.DEFAULT_TAB).assertTextContains("OTPs")
            composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        }

        assertFalse(SettingsActivity.getAllTabEnabled(context))
        assertFalse(SettingsActivity.getPromoNotificationsEnabled(context))
        assertEquals(SettingsActivity.DefaultTab.OTP, SettingsActivity.getDefaultTab(context))
    }

    @Test
    fun sendToPrefillAndSmsPartCountersStayStable() {
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:9988776655?body=Hello%20from%20intent"),
            context,
            NewMessageActivity::class.java
        )

        ActivityScenario.launch<NewMessageActivity>(intent).use { scenario ->
            composeRule.onNodeWithText("9988776655").assertIsDisplayed()
            composeRule.onNodeWithTag(NewMessageTestTags.BODY).assertTextContains("Hello from intent")
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsEnabled()
            composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Send").assertIsDisplayed()

            composeRule.onNodeWithTag(NewMessageTestTags.BODY).performTextReplacement("a".repeat(161))
            composeRule.onNodeWithTag(NewMessageTestTags.COUNTER).assertTextContains("145")
            composeRule.onNodeWithTag(NewMessageTestTags.PARTS).assertTextContains("2 SMS")

            composeRule.onNodeWithTag(NewMessageTestTags.BODY).performTextReplacement("₹".repeat(71))
            composeRule.onNodeWithTag(NewMessageTestTags.COUNTER).assertTextContains("63")
            composeRule.onNodeWithTag(NewMessageTestTags.PARTS).assertTextContains("2 SMS")

            scenario.recreate()
            composeRule.onNodeWithText("9988776655").assertIsDisplayed()
            composeRule.onNodeWithTag(NewMessageTestTags.BODY).assertTextContains("₹".repeat(71))
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsEnabled()
        }
    }

    @Test
    fun rawRecipientCanBeAddedAndRemovedWithBackspace() {
        ActivityScenario.launch(NewMessageActivity::class.java).use {
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsNotEnabled()
            composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT).performTextReplacement("9988776655")
            composeRule.onNodeWithText("Send SMS to 9988776655").performClick()
            composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT).performTextReplacement("8877665544")
            composeRule.onNodeWithText("Send SMS to 8877665544").performClick()
            composeRule.onNodeWithTag(NewMessageTestTags.BODY).performTextReplacement("Hello")
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsEnabled()

            composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT)
                .performClick()
                .performKeyInput { pressKey(Key.Backspace) }
            composeRule.onNodeWithText("9988776655").assertIsDisplayed()
            composeRule.onNodeWithText("8877665544").assertDoesNotExist()
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsEnabled()
            composeRule.onNodeWithContentDescription("Remove 9988776655").performClick()
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsNotEnabled()
            composeRule.onNodeWithText("9988776655").assertDoesNotExist()
            composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT).performTextReplacement("not a number")
            composeRule.onNodeWithText("Send SMS to not a number").assertDoesNotExist()
        }
    }

    @Test
    fun seededInboxSupportsTabsSearchAndPersonalThread() {
        prepareSeededInbox()
        SettingsActivity.setAllTabEnabled(context, true)
        SettingsActivity.setDefaultTab(context, SettingsActivity.DefaultTab.OTP)
        val momThreadId = threadIdFor("+919876543210")
        val googleMessageId = messageIdFor("VK-GOOGLE-T", "G-892341")
        val axisMessageId = messageIdFor("VK-AXISBK-T", "OTP for txn")

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(MainInboxTestTags.otpCode(googleMessageId))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("All").assertIsDisplayed()
            composeRule.onNodeWithText("OTPs").assertIsDisplayed()
            composeRule.onNodeWithText("Personal").assertIsDisplayed()
            composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).assertIsSelected()
            composeRule.onNodeWithTag(MainInboxTestTags.tabUnread(0), useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag(MainInboxTestTags.tabUnread(1), useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag(MainInboxTestTags.tabUnread(2), useUnmergedTree = true).assertExists()
            composeRule.onNodeWithTag(MainInboxTestTags.otpCode(googleMessageId)).performClick()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("892341", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            assertFalse(messageIsRead(googleMessageId))
            assertFalse(messageIsRead(axisMessageId))
            composeRule.onNodeWithTag(MainInboxTestTags.otp(googleMessageId))
                .performSemanticsAction(SemanticsActions.OnLongClick)
            composeRule.onNodeWithContentDescription("Select all items").performClick()
            composeRule.onNodeWithContentDescription("Mark selected conversations or messages as read").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { messageIsRead(googleMessageId) && messageIsRead(axisMessageId) }

            composeRule.onNodeWithText("Personal").performClick()
            assertTrue(threadHasUnread(momThreadId))
            composeRule.onNodeWithTag(MainInboxTestTags.thread(momThreadId)).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.OnLongClick)
            composeRule.onNodeWithText("Mom").assertIsDisplayed()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("1 selected").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("1 selected").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Delete selected conversations or messages").performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.DELETE_DIALOG).assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.onNodeWithContentDescription("Mark selected conversations or messages as read").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { !threadHasUnread(momThreadId) }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(MainInboxTestTags.MORE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(MainInboxTestTags.MORE).performClick()
            composeRule.onNodeWithText("Unread only").performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.UNREAD_CHIP).assertIsDisplayed().performClick()

            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH).performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH_INPUT).performTextReplacement("RATNADEEP")
            composeRule.onNodeWithTag(MainInboxTestTags.CLEAR_SEARCH).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Close search").performClick()
            composeRule.onNodeWithText("Messages").assertIsDisplayed()
        }

        val intent = Intent(context, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", momThreadId)
            putExtra("CONTACT_NAME", "Mom")
            putExtra("CONTACT_ADDRESS", "+919876543210")
            putExtra("CATEGORY", MessageCategory.PERSONAL.name)
        }
        ActivityScenario.launch<ThreadDetailActivity>(intent).use {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(ThreadDetailTestTags.COMPOSER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ThreadDetailTestTags.CONTACT_NAME).assertTextContains("Mom")
            composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER_INPUT).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Call").assertIsDisplayed()
            composeRule.onNodeWithTag(ThreadDetailTestTags.SEND).assertIsDisplayed()
        }
    }

    @Test
    fun serviceThreadDoesNotExposeReplyControls() {
        prepareSeededInbox()
        val threadId = threadIdFor("BP-FLPKRT-S")
        val intent = Intent(context, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", threadId)
            putExtra("CONTACT_NAME", "BP-FLPKRT-S")
            putExtra("CONTACT_ADDRESS", "BP-FLPKRT-S")
            putExtra("CATEGORY", MessageCategory.SERVICE.name)
        }

        ActivityScenario.launch<ThreadDetailActivity>(intent).use {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(
                    "Your order 7283910 worth Rs.1,499 will be delivered by 5pm today. Track at flipkart.com/track/7283910"
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ThreadDetailTestTags.CONTACT_NAME).assertTextContains("BP-FLPKRT-S")
            composeRule.onNodeWithTag(ThreadDetailTestTags.COMPOSER).assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Call").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
            val body = "Your order 7283910 worth Rs.1,499 will be delivered by 5pm today. Track at flipkart.com/track/7283910"
            composeRule.onNodeWithText(body).assertIsDisplayed()
            val spans = LinkifyUtil.linkify(body)
            assertTrue(spans.getSpans(0, spans.length, URLSpan::class.java).any { it.url.startsWith("http") })
        }
    }

    @Test
    fun threadTargetAndCardLastFourLinkGuardStayStable() {
        prepareSeededInbox()
        val targetId = messageIdFor("VM-HDFCBK-T", "Rs.1,250.00 credited")
        val targetIntent = Intent(context, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", threadIdFor("VM-HDFCBK-T"))
            putExtra("CONTACT_NAME", "VM-HDFCBK-T")
            putExtra("CONTACT_ADDRESS", "VM-HDFCBK-T")
            putExtra("CATEGORY", MessageCategory.TRANSACTIONAL.name)
            putExtra("TARGET_MESSAGE_ID", targetId)
        }
        ActivityScenario.launch<ThreadDetailActivity>(targetIntent).use {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(ThreadDetailTestTags.message(targetId))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ThreadDetailTestTags.message(targetId)).assertIsDisplayed()
        }

        val body = "OTP for txn of Rs.2,500 to AMAZON on card ending 4521 is 458291. Valid for 5 min. Do not share. -Axis Bank"
        val spans = LinkifyUtil.linkify(body)
        assertFalse(spans.getSpans(0, spans.length, URLSpan::class.java).any { it.url == "tel:4521" })
    }

    @Test
    fun seededStatsLoadFromTheSmsProvider() {
        prepareSeededInbox()

        ActivityScenario.launch(StatsActivity::class.java).use {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag(StatsTestTags.TOTAL_MESSAGES)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Stats for nerds").assertIsDisplayed()
            composeRule.onNodeWithTag(StatsTestTags.TOTAL_MESSAGES).assertIsDisplayed()
            composeRule.onNodeWithTag(StatsTestTags.TOTAL_THREADS).assertIsDisplayed()
            composeRule.onNodeWithText("Messages per day").assertIsDisplayed()
        }
    }

    @Test
    fun performanceSeedHonorsMessageAndThreadCounts() {
        ensureSmsRole()
        shell(
            "am broadcast -n $packageName/.DebugSeedReceiver " +
                "-a com.praveenpuglia.cleansms.DEBUG_SEED --ei count 120"
        )

        val threadIds = mutableSetOf<Long>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.SERVICE_CENTER} = ?",
            arrayOf("CLEAN_SMS_DEBUG_SEED"),
            null
        )!!.use { cursor ->
            assertEquals(120, cursor.count)
            while (cursor.moveToNext()) threadIds += cursor.getLong(0)
        }
        assertEquals(12, threadIds.size)
    }

    private fun prepareSeededInbox() {
        ensureSmsRole()
        prefs.edit().putBoolean("onboarding_completed", true).commit()
        shell("pm grant $packageName android.permission.WRITE_CONTACTS")
        shell(
            "am broadcast -n $packageName/.DebugSeedReceiver " +
                "-a com.praveenpuglia.cleansms.DEBUG_SEED"
        )
    }

    private fun threadIdFor(address: String): Long {
        repeat(20) {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(address),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
            SystemClock.sleep(250)
        }
        error("No seeded thread found for the requested fixture")
    }

    private fun messageIdFor(address: String, bodyPrefix: String): Long {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} LIKE ?",
            arrayOf(address, "$bodyPrefix%"),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        error("No seeded message found for the requested fixture")
    }

    private fun messageIsRead(id: Long): Boolean = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.READ),
        "${Telephony.Sms._ID} = ?",
        arrayOf(id.toString()),
        null,
    )?.use { cursor -> cursor.moveToFirst() && cursor.getInt(0) != 0 } == true

    private fun threadHasUnread(id: Long): Boolean = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms._ID),
        "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
        arrayOf(id.toString()),
        null,
    )?.use { cursor -> cursor.moveToFirst() } == true

    private fun ensureSmsRole() {
        shell("cmd role add-role-holder android.app.role.SMS $packageName 0")
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
