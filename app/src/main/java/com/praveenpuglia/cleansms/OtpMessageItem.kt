package com.praveenpuglia.cleansms

data class OtpMessageItem(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val otpCode: String,
    val subscriptionId: Int? = null, // platform subscription id (sub_id column)
    val simSlot: Int? = null, // resolved SIM slot index (1-based for UI: 1 or 2)
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val contactLookupUri: String? = null,
    val isRead: Boolean = true
) {
    val isUnread: Boolean
        get() = !isRead
    val hasSavedContact: Boolean
        get() = !contactLookupUri.isNullOrBlank()
}
