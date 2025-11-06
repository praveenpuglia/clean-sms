package com.praveenpuglia.cleansms

data class ContactSuggestion(
    val contactId: Long,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?,
    val lookupKey: String?,
    val isRawNumber: Boolean = false
)
