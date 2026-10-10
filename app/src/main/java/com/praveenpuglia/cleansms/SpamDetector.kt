package com.praveenpuglia.cleansms

/**
 * Operators label suspected spam by prefixing the body: "Airtel Warning: SPAM | ..." and
 * "Jio Alert : SPAM ...". Only a prefix counts, so a forwarded or quoted label doesn't.
 */
object SpamDetector {
    private val operatorSpamPrefix = Regex("""^(?:Airtel\s+Warning|Jio\s+Alert)\s*:\s*SPAM\b\s*[|:\-]?\s*""", RegexOption.IGNORE_CASE)

    fun isSpam(messageBody: String?): Boolean =
        !messageBody.isNullOrBlank() && operatorSpamPrefix.containsMatchIn(messageBody.trim())

    /** Body without the operator's label. */
    fun getCleanBody(messageBody: String?): String {
        if (messageBody.isNullOrBlank()) return ""
        val trimmed = messageBody.trim()
        val label = operatorSpamPrefix.find(trimmed) ?: return messageBody
        return trimmed.substring(label.range.last + 1)
    }
}
