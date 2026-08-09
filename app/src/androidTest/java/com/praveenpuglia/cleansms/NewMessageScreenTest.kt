package com.praveenpuglia.cleansms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageScreen
import com.praveenpuglia.cleansms.ui.newmessage.NewMessageTestTags
import com.praveenpuglia.cleansms.ui.newmessage.smsCounter
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NewMessageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recipientBodyAndSimStateDriveTheComposerActions() {
        val raw = ContactSuggestion(-1, "9988776655", "9988776655", null, null, isRawNumber = true)
        var recipients by mutableStateOf(emptyList<ContactSuggestion>())
        var query by mutableStateOf("")
        var message by mutableStateOf("")
        var sim by mutableIntStateOf(1)
        var sends = 0
        composeRule.setContent {
            CleanSmsTheme {
                NewMessageScreen(
                    recipients = recipients,
                    recipientQuery = query,
                    message = message,
                    suggestions = if (query.isBlank()) emptyList() else listOf(raw),
                    counter = smsCounter(message),
                    selectedSimNumber = sim,
                    showSimSelector = true,
                    onBack = {},
                    onRecipientQueryChange = { query = it },
                    onRecipientSelected = { recipients = recipients + it; query = "" },
                    onRecipientRemoved = { recipients = recipients - it },
                    onRemoveLastRecipient = { false },
                    onMessageChange = { message = it },
                    onSimToggle = { sim = if (sim == 1) 2 else 1 },
                    onSend = { sends++ },
                )
            }
        }

        composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsNotEnabled()
        composeRule.onNodeWithTag(NewMessageTestTags.BODY).performTextReplacement("Hello")
        composeRule.onNodeWithTag(NewMessageTestTags.RECIPIENT_INPUT).performTextReplacement("9988776655")
        composeRule.onNodeWithText("Send SMS to 9988776655").performClick()
        composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, sends) }

        composeRule.onNodeWithContentDescription("Send using SIM 1").performClick()
        composeRule.onNodeWithContentDescription("Send using SIM 2").assertExists()
        composeRule.onNodeWithContentDescription("Remove 9988776655").performClick()
        composeRule.onNodeWithTag(NewMessageTestTags.SEND).assertIsNotEnabled()
        composeRule.onNodeWithTag(NewMessageTestTags.COUNTER).assertTextContains("5 / 160")
    }
}
