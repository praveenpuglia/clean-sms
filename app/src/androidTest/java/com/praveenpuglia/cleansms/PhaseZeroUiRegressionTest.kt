package com.praveenpuglia.cleansms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseZeroUiRegressionTest {
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
            onView(withText("Welcome to Clean SMS")).check(matches(isDisplayed()))
            onView(withId(R.id.set_default_button)).check(matches(not(isEnabled())))
            onView(withId(R.id.continue_button)).check(matches(isEnabled()))
            onView(withContentDescription("Clean SMS logo")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settingsPersistAndDisablingAllRepairsTheDefaultTab() {
        SettingsActivity.setAllTabEnabled(context, true)
        SettingsActivity.setDefaultTab(context, SettingsActivity.DefaultTab.ALL)
        SettingsActivity.setPromoNotificationsEnabled(context, true)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.switch_all_tab)).check(matches(isChecked()))
            onView(withId(R.id.default_tab_button)).check(matches(withText("All")))
            onView(withId(R.id.switch_promo_notifications)).perform(click())
            onView(withId(R.id.switch_promo_notifications)).check(matches(isNotChecked()))
            onView(withId(R.id.all_tab_row)).perform(click())
            onView(withId(R.id.switch_all_tab)).check(matches(isNotChecked()))
            onView(withId(R.id.default_tab_button)).check(matches(withText("OTPs")))
            onView(withContentDescription("Back")).check(matches(isDisplayed()))
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

        ActivityScenario.launch<NewMessageActivity>(intent).use {
            onView(withText("9988776655")).check(matches(isDisplayed()))
            onView(withId(R.id.new_message_body_input)).check(matches(withText("Hello from intent")))
            onView(withId(R.id.new_message_send_button)).check(matches(isEnabled()))
            onView(withContentDescription("Back")).check(matches(isDisplayed()))
            onView(withContentDescription("Send")).check(matches(isDisplayed()))

            onView(withId(R.id.new_message_body_input)).perform(replaceText("a".repeat(161)))
            onView(withId(R.id.new_message_counter)).check(matches(withText("145")))
            onView(withId(R.id.new_message_sms_parts)).check(matches(withText("2 SMS")))

            onView(withId(R.id.new_message_body_input)).perform(replaceText("₹".repeat(71)))
            onView(withId(R.id.new_message_counter)).check(matches(withText("63")))
            onView(withId(R.id.new_message_sms_parts)).check(matches(withText("2 SMS")))
        }
    }

    @Test
    fun rawRecipientCanBeAddedAndRemovedWithBackspace() {
        ActivityScenario.launch(NewMessageActivity::class.java).use { scenario ->
            onView(withId(R.id.new_message_send_button)).check(matches(not(isEnabled())))
            onView(withId(R.id.new_message_recipient_input)).perform(click(), replaceText("9988776655"))
            SystemClock.sleep(500)
            onView(withText("Send SMS to 9988776655")).perform(click())
            onView(withId(R.id.new_message_body_input)).perform(replaceText("Hello"))
            onView(withId(R.id.new_message_send_button)).check(matches(isEnabled()))

            onView(withId(R.id.new_message_recipient_input)).perform(click(), pressKey(KeyEvent.KEYCODE_DEL))
            onView(withId(R.id.new_message_send_button)).check(matches(not(isEnabled())))
            scenario.onActivity { activity ->
                assertEquals(0, activity.findViewById<ChipGroup>(R.id.new_message_recipients_chip_group).childCount)
                assertFalse(activity.findViewById<FloatingActionButton>(R.id.new_message_send_button).isEnabled)
            }
        }
    }

    @Test
    fun seededInboxSupportsTabsSearchAndPersonalThread() {
        prepareSeededInbox()
        SettingsActivity.setAllTabEnabled(context, true)
        SettingsActivity.setDefaultTab(context, SettingsActivity.DefaultTab.OTP)

        ActivityScenario.launch(MainActivity::class.java).use {
            SystemClock.sleep(2_000)
            onView(withText("All")).check(matches(isDisplayed()))
            onView(withText("OTPs")).check(matches(isDisplayed()))
            onView(withText("Personal")).check(matches(isDisplayed()))
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
