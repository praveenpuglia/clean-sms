package com.praveenpuglia.cleansms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingScreen
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingTestTags
import com.praveenpuglia.cleansms.ui.onboarding.OnboardingUiState
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun continueRequiresBothSetupStepsAndCompletedStepsCannotBeRepeated() {
        var state by mutableStateOf(OnboardingUiState())
        var defaultClicks = 0
        var backgroundClicks = 0
        var continueClicks = 0
        composeRule.setContent {
            CleanSmsTheme {
                OnboardingScreen(
                    state = state,
                    onSetDefault = { defaultClicks++ },
                    onAllowBackground = { backgroundClicks++ },
                    onContinue = { continueClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(OnboardingTestTags.SET_DEFAULT).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.ALLOW_BACKGROUND).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, defaultClicks)
            assertEquals(1, backgroundClicks)
        }

        composeRule.runOnIdle { state = state.copy(isDefaultSmsApp = true) }
        composeRule.onNodeWithTag(OnboardingTestTags.SET_DEFAULT).assertIsNotEnabled()
        composeRule.onNodeWithTag(OnboardingTestTags.ALLOW_BACKGROUND).assertIsEnabled()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsNotEnabled()

        composeRule.runOnIdle { state = state.copy(isBatteryOptimizationIgnored = true) }
        composeRule.onNodeWithTag(OnboardingTestTags.ALLOW_BACKGROUND).assertIsNotEnabled()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, continueClicks) }
    }
}
