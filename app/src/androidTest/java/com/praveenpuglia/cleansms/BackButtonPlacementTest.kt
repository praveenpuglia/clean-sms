package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.praveenpuglia.cleansms.ui.inbox.MainInboxTestTags
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3 top app bar: the 48dp navigation button starts 4dp from the edge, so the 24dp icon sits on
 * the 16dp content keyline (icon center at 28dp). Every screen's back button must share that position.
 */
@RunWith(AndroidJUnit4::class)
class BackButtonPlacementTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        AppSettings.setOnboardingCompleted(context)
    }

    @Test
    fun settings() = assertBackAtKeyline(Intent(context, SettingsActivity::class.java))

    @Test
    fun stats() = assertBackAtKeyline(Intent(context, StatsActivity::class.java))

    @Test
    fun newMessage() = assertBackAtKeyline(Intent(context, NewMessageActivity::class.java))

    @Test
    fun thread() = assertBackAtKeyline(
        ThreadDetailActivity.intent(context, Long.MAX_VALUE, "+15550100099", null, null, null, MessageCategory.PERSONAL),
    )

    @Test
    fun inboxSearch() = assertBackAtKeyline(Intent(context, MainActivity::class.java), "Close search") {
        composeRule.onNodeWithTag(MainInboxTestTags.SEARCH).performClick()
    }

    private fun assertBackAtKeyline(intent: Intent, description: String = "Back", open: () -> Unit = {}) {
        ActivityScenario.launch<Activity>(intent).use {
            open()
            // Assert the icon, not the button bounds: M3 IconButton reports 40dp bounds inside its 48dp
            // touch target, while a 48dp-sized one reports 48dp. Both center the icon at 4 + 24 = 28dp.
            val bounds = composeRule.onNodeWithContentDescription(description).getBoundsInRoot()
            val center = (bounds.left + bounds.right) / 2
            assertEquals(28f, center.value, 0.5f)
        }
    }
}
