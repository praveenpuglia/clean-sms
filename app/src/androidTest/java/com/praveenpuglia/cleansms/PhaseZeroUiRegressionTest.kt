package com.praveenpuglia.cleansms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingTestTags
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageTestTags
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun incompleteOnboardingShowsSetupAndAccessibleActions() {
        ensureSmsRole()
        prefs.edit().putBoolean("onboarding_completed", false).commit()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Welcome to Clean SMS").assertIsDisplayed()
            composeRule.onNodeWithTag(OnboardingTestTags.SET_DEFAULT).assertIsNotEnabled()
            composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsEnabled()
            composeRule.onNodeWithContentDescription("Clean SMS logo").assertIsDisplayed()
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
            composeRule.onNodeWithTag(NewMessageTestTags.BODY).performTextReplacement("Hello")
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsEnabled()

            composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT)
                .performClick()
                .performKeyInput { pressKey(Key.Backspace) }
            composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsNotEnabled()
            composeRule.onNodeWithText("9988776655").assertDoesNotExist()
        }
    }

    @Test
    fun seededInboxSupportsTabsSearchAndPersonalThread() {
        prepareSeededInbox()
        SettingsActivity.setAllTabEnabled(context, true)
        SettingsActivity.setDefaultTab(context, SettingsActivity.DefaultTab.OTP)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(2_000)
            onView(withText("All")).check(matches(isDisplayed()))
            onView(withText("OTPs")).check(matches(isDisplayed()))
            onView(withText("Personal")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val tabs = activity.findViewById<TabLayout>(R.id.category_tabs)
                val allUnreadDot = tabs.getTabAt(0)?.customView
                    ?.findViewById<View>(R.id.tab_unread_dot)
                assertEquals(View.VISIBLE, allUnreadDot?.visibility)
            }
            onView(withContentDescription("Search messages")).perform(click())
            onView(withId(R.id.search_input)).check(matches(isDisplayed()))
            onView(withId(R.id.search_input)).perform(replaceText("RATNADEEP"))
            onView(withContentDescription("Clear search")).check(matches(isDisplayed()))
            // First Back dismisses the IME; the next Back exits search mode.
            pressBack()
            pressBack()
            onView(withText("Messages")).check(matches(isDisplayed()))
        }

        val threadId = threadIdFor("+919876543210")
        val intent = Intent(context, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", threadId)
            putExtra("CONTACT_NAME", "Mom")
            putExtra("CONTACT_ADDRESS", "+919876543210")
            putExtra("CATEGORY", MessageCategory.PERSONAL.name)
        }
        ActivityScenario.launch<ThreadDetailActivity>(intent).use {
            SystemClock.sleep(1_000)
            onView(withText("Mom")).check(matches(isDisplayed()))
            onView(withId(R.id.composer_message_input)).check(matches(isDisplayed()))
            onView(withContentDescription("Call")).check(matches(isDisplayed()))
            onView(withContentDescription("Send")).check(matches(isDisplayed()))
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
            SystemClock.sleep(1_000)
            onView(withId(R.id.thread_contact_name)).check(matches(withText("BP-FLPKRT-S")))
            onView(withId(R.id.composer_message_input)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.call_button)).check(matches(withEffectiveVisibility(GONE)))
            onView(withContentDescription("Back")).check(matches(isDisplayed()))
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

    private fun ensureSmsRole() {
        shell("cmd role add-role-holder android.app.role.SMS $packageName 0")
    }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
