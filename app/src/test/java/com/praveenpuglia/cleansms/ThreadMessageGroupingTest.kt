package com.praveenpuglia.cleansms

import com.praveenpuglia.cleansms.ui.thread.createMessageListItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ThreadMessageGroupingTest {
    @Test
    fun todaySkipsDayLabelWhileYesterdayKeepsIt() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 8, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)

        val todayItems = createMessageListItems(listOf(message(1, now.timeInMillis)), now.timeInMillis)
        val yesterdayItems = createMessageListItems(listOf(message(2, yesterday.timeInMillis)), now.timeInMillis)

        assertEquals(listOf(MessageListItem.MessageItem(message(1, now.timeInMillis))), todayItems)
        assertTrue(yesterdayItems.first() is MessageListItem.DayIndicator)
        assertEquals("Yesterday", (yesterdayItems.first() as MessageListItem.DayIndicator).label)
    }

    private fun message(id: Long, date: Long) = Message(id, 1, "sender", "body", date, 1)
}
