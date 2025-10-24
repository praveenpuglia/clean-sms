package com.praveenpuglia.cleansms

data class ThreadItem(
    val threadId: Long,
    val nameOrAddress: String,
    val date: Long,
    val snippet: String,
    val contactName: String? = null,
    val contactPhotoUri: String? = null,
    val category: MessageCategory = MessageCategory.UNKNOWN
)
