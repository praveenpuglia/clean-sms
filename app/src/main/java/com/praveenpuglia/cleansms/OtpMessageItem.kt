package com.praveenpuglia.cleansms

data class OtpMessageItem(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val otpCode: String,
    val contactName: String? = null,
    val contactPhotoUri: String? = null
)
