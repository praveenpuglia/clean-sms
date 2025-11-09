package com.praveenpuglia.cleansms

sealed class MessageListItem {
    data class MessageItem(val message: Message) : MessageListItem()
    data class DayIndicator(val timestamp: Long, val label: String) : MessageListItem()
}
