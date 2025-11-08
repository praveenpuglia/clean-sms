package com.praveenpuglia.cleansms

object SpamDetector {
    private const val AIRTEL_SPAM_PREFIX = "Airtel Warning: SPAM"
    
    fun isSpam(messageBody: String?): Boolean {
        if (messageBody.isNullOrBlank()) return false
        return messageBody.trim().startsWith(AIRTEL_SPAM_PREFIX, ignoreCase = true)
    }
    
    fun getCleanBody(messageBody: String?): String {
        if (messageBody.isNullOrBlank()) return ""
        val trimmed = messageBody.trim()
        return if (trimmed.startsWith(AIRTEL_SPAM_PREFIX, ignoreCase = true)) {
            trimmed.removePrefix(AIRTEL_SPAM_PREFIX).trim()
        } else {
            messageBody
        }
    }
}
