package com.praveenpuglia.cleansms

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 = received, 2 = sent
    val subscriptionId: Int? = null, // platform subscription id (sub_id)
    val simSlot: Int? = null // resolved SIM slot (1 or 2) for UI; null if unknown
)
