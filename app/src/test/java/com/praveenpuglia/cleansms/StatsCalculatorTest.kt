package com.praveenpuglia.cleansms

import com.praveenpuglia.cleansms.ui.stats.StatsMessageRecord
import com.praveenpuglia.cleansms.ui.stats.StatsPeriod
import com.praveenpuglia.cleansms.ui.stats.buildStatsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatsCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private val now = millis(2026, 8, 9, 12)

    @Test
    fun selectedPeriodCountsMessagesThreadsCategoriesAndHabits() {
        val records = listOf(
            record(1, millis(2026, 8, 9, 10), 1, MessageCategory.PERSONAL, unread = true, otp = true),
            record(1, millis(2026, 8, 8, 9), 2, MessageCategory.PERSONAL),
            record(2, millis(2026, 8, 8, 9), 1, MessageCategory.TRANSACTIONAL, spam = true),
            record(3, millis(2026, 7, 30, 8), 1, MessageCategory.SERVICE),
            record(4, millis(2026, 7, 9, 8), 1, MessageCategory.PROMOTIONAL),
            record(5, millis(2026, 8, 10, 8), 1, MessageCategory.SERVICE),
        )

        val summary = buildStatsSummary(records, StatsPeriod.SEVEN_DAYS, now, zone)

        assertEquals(3, summary.totalMessages)
        assertEquals(2, summary.totalThreads)
        assertEquals(2, summary.receivedMessages)
        assertEquals(1, summary.sentMessages)
        assertEquals(1, summary.unreadMessages)
        assertEquals(1, summary.otpMessages)
        assertEquals(1, summary.spamMessages)
        assertEquals(3.0 / 7.0, summary.averagePerDay, 0.001)
        assertEquals(7, summary.activity.size)
        assertEquals(1, summary.activity.last().count)
        assertEquals(2, summary.categories.first { it.category == MessageCategory.PERSONAL }.messages)
        assertEquals(1, summary.categories.first { it.category == MessageCategory.PERSONAL }.threads)
        assertEquals(9, summary.busiestHour)
    }

    @Test
    fun allTimeUsesMonthlyActivityAndFillsEmptyMonths() {
        val records = listOf(
            record(1, millis(2026, 1, 2, 8), 1, MessageCategory.PERSONAL),
            record(2, millis(2026, 3, 2, 8), 1, MessageCategory.SERVICE),
            record(3, millis(2026, 8, 2, 8), 1, MessageCategory.GOVERNMENT),
        )

        val summary = buildStatsSummary(records, StatsPeriod.ALL_TIME, now, zone)

        assertEquals(3, summary.totalMessages)
        assertEquals(3, summary.totalThreads)
        assertEquals(8, summary.activity.size)
        assertEquals(listOf(1, 0, 1), summary.activity.take(3).map { it.count })
        assertTrue(summary.activityIsMonthly)
        assertEquals("2026-01-02", summary.oldestMessageDate.toString())
    }

    @Test
    fun emptyInboxProducesZeroedStats() {
        val summary = buildStatsSummary(emptyList(), StatsPeriod.THIRTY_DAYS, now, zone)

        assertEquals(0, summary.totalMessages)
        assertEquals(0, summary.totalThreads)
        assertEquals(30, summary.activity.size)
        assertTrue(summary.categories.isEmpty())
        assertNull(summary.oldestMessageDate)
        assertNull(summary.busiestDay)
        assertNull(summary.busiestHour)
    }

    private fun record(
        threadId: Long,
        date: Long,
        type: Int,
        category: MessageCategory,
        unread: Boolean = false,
        otp: Boolean = false,
        spam: Boolean = false,
    ) = StatsMessageRecord(threadId, date, type, unread, category, otp, spam)

    private fun millis(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
}
