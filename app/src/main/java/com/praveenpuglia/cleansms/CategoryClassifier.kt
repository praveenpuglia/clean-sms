package com.praveenpuglia.cleansms

import android.content.Context
import android.provider.Telephony
import androidx.core.net.toUri
import java.util.regex.Pattern

object CategoryClassifier {

    // TRAI header handling (refined):
    // Formal pattern: XY-HEADER-SFX
    // - XY          : 2 alpha chars (TSP + service area codes)
    // - HEADER      : 1–6 alphanumeric chars (registered sender header; may be shorter than 6)
    // - SFX         : Single alpha suffix bucket in (S, P, T, G)
    // We also allow headers without suffix (XY-HEADER) but only classify when suffix present.
    // Examples: JX-BOLT-S, VM-HDFCBK-T, DZ-AMAZN-P, BK-IRCTC-G
    private val traiWithSuffixPattern = Pattern.compile("^([A-Z]{2})-([A-Z0-9]{1,6})-([A-Z])$", Pattern.CASE_INSENSITIVE)
    private val traiNoSuffixPattern = Pattern.compile("^([A-Z]{2})-([A-Z0-9]{1,6})$", Pattern.CASE_INSENSITIVE)
    
    // Short code pattern: 3-8 digits (service messages from companies, banks, etc.)
    private val shortCodePattern = Pattern.compile("^\\d{3,8}$")
    
    // Full phone number patterns (with or without country code, typically 10+ digits)
    private val phonePattern = Pattern.compile("^\\+?[1-9]\\d{9,14}$")

    // OTP keyword proximity detection (shared with notification logic) per constitution §5 (Non-intrusive, high precision)
    // We require an OTP keyword within a small window (<= 40 chars) of a candidate numeric code (4-8 digits)
    private val otpKeywordRegex = Regex("\\b(otp|one[\\s-]*time\\s*password|verification\\s*code|security\\s*code|login\\s*code)\\b", RegexOption.IGNORE_CASE)
    private val otpCodeRegex = Regex("\\b\\d{4,8}\\b")

    fun extractHighPrecisionOtp(body: String): String? {
        if (body.length > 1000) return null // avoid heavy processing on very long messages
        if (!otpKeywordRegex.containsMatchIn(body)) return null
        // Collect all codes and test proximity to keyword matches
        val keywords = otpKeywordRegex.findAll(body).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        val codes = otpCodeRegex.findAll(body).map { it }.toList()
        for (codeMatch in codes) {
            val codeRange = codeMatch.range
            val near = keywords.any { kw ->
                val distance = if (codeRange.first >= kw.last) codeRange.first - kw.last else kw.first - codeRange.last
                distance in 0..40 // within 40 chars forward or backward
            }
            if (near) return codeMatch.value
        }
        return null
    }

    /**
     * Categorize a sender address based on TRAI format or phone number
     */
    fun categorizeAddress(address: String): MessageCategory {
        // Normalization: trim, collapse whitespace, strip common formatting characters
        // This prevents misclassification of spaced or dashed numbers (e.g. "77384 56881" or "77-384-56881")
        val cleaned = address.trim()
        val digitsOnly = cleaned.filter { it.isDigit() }

        // 1. TRAI header with suffix
        traiWithSuffixPattern.matcher(cleaned.uppercase()).apply {
            if (matches()) {
                val suffix = group(3)
                MessageCategory.fromTraiSuffix(suffix)?.let { return it }
                return MessageCategory.UNKNOWN
            }
        }

        // 2. TRAI header without suffix -> UNKNOWN (will be inferred later)
        if (traiNoSuffixPattern.matcher(cleaned.uppercase()).matches()) {
            return MessageCategory.UNKNOWN
        }

    // 3. Full phone number (after normalization) -> PERSONAL
        if (digitsOnly.length in 10..15) {
            // Accept E.164 with leading + as well as local 10-digit numbers.
            // Extra guard: ensure not a short code inadvertently (length >=10 already ensures this)
            return MessageCategory.PERSONAL
        }

    // 4. Short codes (3–8 digits) -> SERVICE (company/bank system messages)
        if (shortCodePattern.matcher(digitsOnly).matches()) {
            return MessageCategory.SERVICE
        }

    // 5. Other alphanumeric sender IDs: treat as UNKNOWN now, will resolve via content
        // Rationale: Many such headers are promotional but we rely on robust keyword inference to avoid false positives.
        return MessageCategory.UNKNOWN
    }

    /**
     * Analyze message content to infer category for unknown senders
     */
    @Suppress("UNUSED_PARAMETER") // address retained for future heuristics (e.g., contact presence, locale analysis)
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
