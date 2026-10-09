package com.praveenpuglia.cleansms

import android.content.ContentValues
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI state the user built up must survive a configuration change (rotation, dark mode, etc.). */
@RunWith(AndroidJUnit4::class)
class InboxRotationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        AppSettings.setOnboardingCompleted(context)
        AppSettings.setAllTabEnabled(context, false)
        AppSettings.setDefaultTab(context, AppSettings.DefaultTab.OTP)
        cleanUp()
        context.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, SENDER)
                put(Telephony.Sms.BODY, "Rotation fixture")
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 0)
            },
        )
    }

    @After
    fun cleanUp() {
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(SENDER))
    }

    @Test
    fun selectedTabAndUnreadFilterSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Personal").performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.MORE).performClick()
            composeRule.onNodeWithText("Unread only").performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.UNREAD_CHIP).assertIsDisplayed()

            scenario.recreate()

            composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).assertIsSelected()
            composeRule.onNodeWithTag(MainInboxTestTags.UNREAD_CHIP).assertIsDisplayed()
        }
    }

    @Test
    fun searchQuerySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH).performClick()
            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH_INPUT).performTextReplacement("Rotation")

            scenario.recreate()

            composeRule.onNodeWithTag(MainInboxTestTags.SEARCH_INPUT).assertTextContains("Rotation")
        }
    }

    @Test
    fun selectionSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Personal").performClick()
            val threadTag = MainInboxTestTags.thread(threadId())
            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(threadTag).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithTag(threadTag).performSemanticsAction(SemanticsActions.OnLongClick)
            composeRule.onNodeWithText("1 selected").assertIsDisplayed()

            scenario.recreate()

            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("1 selected").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun enablingTheAllTabInSettingsRebuildsTabsOnReturn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Personal").performClick()
            composeRule.onAllNodesWithText("All").assertCountEquals(0)

            scenario.moveToState(Lifecycle.State.CREATED) // user is in Settings
            AppSettings.setAllTabEnabled(context, true)
            scenario.moveToState(Lifecycle.State.RESUMED)

            composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("All").fetchSemanticsNodes().isNotEmpty() }
            // Index shifted by the new tab: lands on the default (OTPs) tab, as before the ViewModel.
            composeRule.onNodeWithTag(MainInboxTestTags.tab(1)).assertIsSelected()
        }
    }

    private fun threadId(): Long =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), "${Telephony.Sms.ADDRESS} = ?", arrayOf(SENDER), null)!!
            .use { check(it.moveToFirst()); it.getLong(0) }

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val SENDER = "+15550100041"
    }
}
