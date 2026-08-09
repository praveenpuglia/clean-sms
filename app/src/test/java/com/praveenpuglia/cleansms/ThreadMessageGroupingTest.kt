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

    @Test
    fun groupsConsecutiveMessagesUnderOneDayIndicator() {
        val now = date(2026, Calendar.AUGUST, 20)
        val firstDay = date(2026, Calendar.AUGUST, 11)
        val secondDay = date(2026, Calendar.AUGUST, 12)
        val messages = listOf(message(1, firstDay), message(2, firstDay + 60_000), message(3, secondDay))

        val items = createMessageListItems(messages, now)

        assertEquals(5, items.size)
        assertEquals("11th Aug, 2026", (items[0] as MessageListItem.DayIndicator).label)
        assertEquals(listOf(1L, 2L, 3L), items.filterIsInstance<MessageListItem.MessageItem>().map { it.message.id })
        assertEquals("12th Aug, 2026", (items[3] as MessageListItem.DayIndicator).label)
    }

    @Test
    fun emptyConversationHasNoListItems() {
        assertTrue(createMessageListItems(emptyList()).isEmpty())
    }

    private fun date(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        set(year, month, day, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun message(id: Long, date: Long) = Message(id, 1, "sender", "body", date, 1)
}
