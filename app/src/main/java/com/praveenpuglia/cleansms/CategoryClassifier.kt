package com.praveenpuglia.cleansms

import android.content.Context
import android.provider.Telephony
import androidx.core.net.toUri
import java.util.regex.Pattern

object CategoryClassifier {

    // TRAI format: XY-ABCDEF-G (e.g., AB-HDFCBK-S, CD-AMAZON-P)
    // X = TSP, Y = Service Area, ABCDEF = Header Name (6 chars), G = Suffix (S/P/T/G)
    private val traiPattern = Pattern.compile("^([A-Z]{2})-([A-Z0-9]{6})-([A-Z])$", Pattern.CASE_INSENSITIVE)
    
    // Short code pattern: 3-8 digits (service messages from companies, banks, etc.)
    private val shortCodePattern = Pattern.compile("^\\d{3,8}$")
    
    // Full phone number patterns (with or without country code, typically 10+ digits)
    private val phonePattern = Pattern.compile("^\\+?[1-9]\\d{9,14}$")

    /**
     * Categorize a sender address based on TRAI format or phone number
     */
    fun categorizeAddress(address: String): MessageCategory {
        val cleaned = address.trim()

        // Check if it's a TRAI format sender ID: XY-ABCDEF-G
        val traiMatcher = traiPattern.matcher(cleaned)
        if (traiMatcher.matches()) {
            val suffix = traiMatcher.group(3) // Get the G suffix (S/P/T/G)
            val category = MessageCategory.fromTraiSuffix(suffix)
            if (category != null) {
                return category
            }
        }

        // Check if it's a short code (e.g., 57575, 59099) - categorize as Service
        if (shortCodePattern.matcher(cleaned).matches()) {
            return MessageCategory.SERVICE
        }

        // Check if it's a full phone number (10+ digits)
        if (phonePattern.matcher(cleaned).matches()) {
            return MessageCategory.PERSONAL
        }

        // For non-TRAI, non-phone senders, return UNKNOWN for now
        // Will be classified later based on content
        return MessageCategory.UNKNOWN
    }

    /**
     * Analyze message content to infer category for unknown senders
     */
    fun inferCategoryFromContent(context: Context, address: String, threadId: Long): MessageCategory {
        // Get a few messages from this thread
        val messages = getSampleMessages(context, threadId, 5)
        if (messages.isEmpty()) return MessageCategory.SERVICE

        val combinedText = messages.joinToString(" ").lowercase()

        // Promotional keywords
        val promotionalKeywords = listOf(
            "offer", "discount", "sale", "deal", "shop", "buy now", "limited time",
            "free", "win", "prize", "exclusive", "coupon", "cashback", "% off"
        )

        // Transactional keywords
        val transactionalKeywords = listOf(
            "otp", "password", "verification", "verify", "code", "pin",
            "debited", "credited", "account", "balance", "payment", "transaction",
            "invoice", "receipt", "order", "confirm", "booking", "ticket"
        )

        // Service keywords
        val serviceKeywords = listOf(
            "subscription", "service", "activated", "renewed", "expire",
            "update", "notification", "alert", "reminder", "due date",
            "recharge", "plan", "validity", "data", "minutes"
        )

        // Government keywords
        val governmentKeywords = listOf(
            "government", "govt", "ministry", "department", "aadhaar", "pan",
            "voter", "election", "tax", "passport", "license", "official"
        )

        // Count keyword matches
        var promotionalScore = 0
        var transactionalScore = 0
        var serviceScore = 0
        var governmentScore = 0

        for (keyword in promotionalKeywords) {
            if (combinedText.contains(keyword)) promotionalScore++
        }
        for (keyword in transactionalKeywords) {
            if (combinedText.contains(keyword)) transactionalScore++
        }
        for (keyword in serviceKeywords) {
            if (combinedText.contains(keyword)) serviceScore++
        }
        for (keyword in governmentKeywords) {
            if (combinedText.contains(keyword)) governmentScore++
        }

        // Return category with highest score
        val maxScore = maxOf(promotionalScore, transactionalScore, serviceScore, governmentScore)
        
        return when {
            maxScore == 0 -> MessageCategory.SERVICE // Default fallback
            promotionalScore == maxScore -> MessageCategory.PROMOTIONAL
            transactionalScore == maxScore -> MessageCategory.TRANSACTIONAL
            serviceScore == maxScore -> MessageCategory.SERVICE
            governmentScore == maxScore -> MessageCategory.GOVERNMENT
            else -> MessageCategory.SERVICE
        }
    }

    private fun getSampleMessages(context: Context, threadId: Long, limit: Int): List<String> {
        val messages = mutableListOf<String>()
        val uri = "content://sms".toUri()
        val projection = arrayOf("body")
        val selection = "thread_id = ?"
        val selectionArgs = arrayOf(threadId.toString())
        val sortOrder = "date DESC LIMIT $limit"

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idxBody = cursor.getColumnIndex("body")
            while (cursor.moveToNext()) {
                if (idxBody >= 0) {
                    val body = cursor.getString(idxBody)
                    if (!body.isNullOrBlank()) {
                        messages.add(body)
                    }
                }
            }
        }
        return messages
    }
}
