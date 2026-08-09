package com.praveenpuglia.cleansms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.praveenpuglia.cleansms.ui.stats.StatsMessageRecord
import com.praveenpuglia.cleansms.ui.stats.StatsPeriod
import com.praveenpuglia.cleansms.ui.stats.StatsScreen
import com.praveenpuglia.cleansms.ui.stats.StatsTestTags
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statsRenderChangePeriodAndNavigateBack() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 8, 9, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val records = listOf(
            record(1, now, MessageCategory.PERSONAL, otp = true),
            record(2, now - 86_400_000, MessageCategory.TRANSACTIONAL, spam = true),
            record(3, now - 60 * 86_400_000L, MessageCategory.SERVICE),
        )
        var wentBack = false

        composeRule.setContent {
            CleanSmsTheme {
                StatsScreen(records, loadFailed = false, onBack = { wentBack = true }, nowMillis = now)
            }
        }

        composeRule.onNodeWithText("Stats for nerds").assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.TOTAL_MESSAGES).assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.ACTIVITY).assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.period(StatsPeriod.ALL_TIME)).performClick()
        composeRule.onNodeWithTag(StatsTestTags.TOTAL_MESSAGES).assertTextEquals("3")
        composeRule.onNodeWithTag(StatsTestTags.CATEGORIES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(StatsTestTags.category(MessageCategory.SERVICE)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertTrue(wentBack) }
    }

    private fun record(
        threadId: Long,
        date: Long,
        category: MessageCategory,
        otp: Boolean = false,
        spam: Boolean = false,
    ) = StatsMessageRecord(threadId, date, 1, false, category, otp, spam)
}
