package com.praveenpuglia.cleansms

import com.praveenpuglia.cleansms.ui.inbox.formatInboxDate
import com.praveenpuglia.cleansms.ui.inbox.highlightedText
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class InboxDateFormattingTest {
    @Test
    fun formatsTodayYesterdayWeekAndOlderMessages() {
        val now = date(2026, Calendar.AUGUST, 8, 12, 0)

        assertEquals("9:05 AM", formatInboxDate(date(2026, Calendar.AUGUST, 8, 9, 5), now))
        assertEquals("Yesterday, 9:05 AM", formatInboxDate(date(2026, Calendar.AUGUST, 7, 9, 5), now))
        assertEquals("Sun, 9:05 AM", formatInboxDate(date(2026, Calendar.AUGUST, 2, 9, 5), now))
        assertEquals("30 Jul, 9:05 AM", formatInboxDate(date(2026, Calendar.JULY, 30, 9, 5), now))
    }

    @Test
    fun formatsClockEdgesAndYesterdayAcrossYearBoundary() {
        val now = date(2026, Calendar.JANUARY, 1, 12, 0)

        assertEquals("12:00 AM", formatInboxDate(date(2026, Calendar.JANUARY, 1, 0, 0), now))
        assertEquals("12:00 PM", formatInboxDate(now, now))
        assertEquals("Yesterday, 11:59 PM", formatInboxDate(date(2025, Calendar.DECEMBER, 31, 23, 59), now))
    }

    @Test
    fun highlightsEveryCaseInsensitiveSearchOccurrence() {
        val highlighted = highlightedText("OTP otp OtP", "otp", Color.Red)

        assertEquals("OTP otp OtP", highlighted.text)
        assertEquals(listOf(0 to 3, 4 to 7, 8 to 11), highlighted.spanStyles.map { it.start to it.end })
        assertEquals(emptyList<Any>(), highlightedText("message", " ", Color.Red).spanStyles)
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
