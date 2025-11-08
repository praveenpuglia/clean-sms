package com.praveenpuglia.cleansms

data class ThreadItem(
    val threadId: Long,
    val nameOrAddress: String,
    val date: Long,
    val snippet: String,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val contactLookupUri: String? = null,
    val category: MessageCategory = MessageCategory.UNKNOWN,
    val unreadCount: Int = 0,
    val hasSpam: Boolean = false
) {
    val hasUnread: Boolean
        get() = unreadCount > 0
    val hasSavedContact: Boolean
        get() = !contactLookupUri.isNullOrBlank()
}
