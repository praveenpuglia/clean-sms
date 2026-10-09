package com.praveenpuglia.cleansms

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLayoutTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun themeAndFontSegmentedRowsHaveTheSameHeight() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            val theme = composeRule.onNodeWithText("Light").getBoundsInRoot()
            val font = composeRule.onAllNodesWithText("Hello!").onFirst().getBoundsInRoot()
            assertEquals((theme.bottom - theme.top).value, (font.bottom - font.top).value, 0.5f)
        }
    }
}
