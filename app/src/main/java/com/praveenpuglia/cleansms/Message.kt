package com.praveenpuglia.cleansms

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1 = received, 2 = sent
)
