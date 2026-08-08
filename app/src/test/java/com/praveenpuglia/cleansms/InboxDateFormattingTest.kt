package com.praveenpuglia.cleansms

import com.praveenpuglia.cleansms.ui.inbox.formatInboxDate
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

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
