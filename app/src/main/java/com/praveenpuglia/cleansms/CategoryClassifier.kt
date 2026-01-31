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

    // ==================== OTP Detection Patterns ====================

    // Strategy 1: Explicit OTP keywords (highest confidence)
    // Comprehensive list including authorization, auth, access, confirmation, etc.
    private val otpKeywordRegex = Regex(
        """(?i)\b(otp|one[\s-]*time[\s-]*password|verification[\s-]*code|security[\s-]*code|login[\s-]*code|authorization[\s-]*code|auth[\s-]*code|access[\s-]*code|confirmation[\s-]*code|authentication[\s-]*code|passcode|pin[\s-]*code|secret[\s-]*code|temporary[\s-]*code|dynamic[\s-]*code)\b"""
    )

    // Code pattern: 4-8 digits (standard OTP length range)
    private val otpCodeRegex = Regex("""\b\d{4,8}\b""")

    // Strategy 2: Code-first pattern - handles "98094 is the authorization code" structure
    // Matches: [DIGITS] [optional words] is [the/your] [TYPE] code
    private val codeFirstPattern = Regex(
        """(?i)\b(\d{4,8})\s+(?:\w+\s+)?is\s+(?:the\s+)?(?:your\s+)?(?:\w+\s+)?code\b"""
    )

    // Strategy 3: Generic "code" with context - "the code is", "your code:", etc.
    // Requires contextual words before "code" to avoid false positives
    private val genericCodeContextPattern = Regex(
        """(?i)\b(?:the|your|is\s+the|is\s+your)\s+(?:\w+\s+)?code\s*(?:is|:)?\s*(\d{4,8})\b"""
    )

    // Alternative: "code for [service] is [DIGITS]"
    private val codeForPattern = Regex(
        """(?i)\bcode\s+(?:for\s+)?(?:your\s+)?(?:\w+\s+){0,3}(?:is|:)\s*(\d{4,8})\b"""
    )

    // Strategy 4: Time-validity indicators (strong OTP signal)
    private val timeValidityPattern = Regex(
        """(?i)\b(?:valid|expires?|use\s+(?:it\s+)?within|expir(?:es|ing)\s+in)\s+(?:for\s+)?(\d+)\s*(?:min(?:ute)?s?|sec(?:ond)?s?|hours?|hrs?)\b"""
    )

    // Strategy 5: "Do not share" warnings (common in OTP messages)
    private val doNotSharePattern = Regex(
        """(?i)\b(?:do\s+not\s+share|don'?t\s+share|never\s+share|keep\s+(?:it\s+)?(?:safe|secret|confidential)|not\s+share\s+(?:it\s+)?with\s+anyone)\b"""
    )

    // Pattern to detect monetary amounts - numbers preceded by currency indicators
    // Used to filter out false positives
    private val monetaryPrefixRegex = Regex("""(?i)(rs\.?|inr|₹)\s*\d""")

    // Excluded code types (to avoid false positives)
    private val excludedCodeTypes = setOf(
        "promo", "coupon", "zip", "postal", "area", "country", "discount",
        "voucher", "gift", "referral", "ref", "order", "tracking", "booking",
        "reservation", "flight", "pnr", "ticket"
    )

    // Pattern for "is XXXX" which commonly follows OTP mentions
    private val otpIsPatternRegex = Regex("""(?i)\bis\s*:?\s*(\d{4,8})\b""")

    /**
     * Extract OTP code using multi-strategy cascade approach.
     * Strategies are ordered by confidence level (highest first).
     */
    fun extractHighPrecisionOtp(body: String): String? {
        if (body.length > 1000) return null // avoid heavy processing on very long messages

        // Strategy 1: Explicit OTP keywords with proximity (highest confidence)
        extractWithExplicitKeywords(body)?.let { return it }

        // Strategy 2: Code-first patterns (e.g., "98094 is the authorization code")
        extractCodeFirstPattern(body)?.let { return it }

        // Strategy 3: Generic "code" with context validation
        extractGenericCodePattern(body)?.let { return it }

        // Strategy 4: Time-validity + do-not-share indicators (contextual signals)
        extractWithContextualIndicators(body)?.let { return it }

        return null
    }

    /**
     * Strategy 1: Look for explicit OTP keywords with code in proximity
     */
    private fun extractWithExplicitKeywords(body: String): String? {
        if (!otpKeywordRegex.containsMatchIn(body)) return null

        val otpKeywordMatch = otpKeywordRegex.find(body) ?: return null

        // First, try "is XXXX" pattern after keyword
        val afterKeyword = body.substring(minOf(otpKeywordMatch.range.last + 1, body.length))
        val isPatternMatch = otpIsPatternRegex.find(afterKeyword)
        if (isPatternMatch != null) {
            val code = isPatternMatch.groupValues[1]
            if (isValidOtpCode(body, otpKeywordMatch.range.last + 1 + isPatternMatch.range.first, code)) {
                return code
            }
        }

        // Proximity-based: find codes near keywords
        val keywords = otpKeywordRegex.findAll(body).map { it.range }.toList()
        val codes = otpCodeRegex.findAll(body).toList()

        // Filter and find valid codes
        for (codeMatch in codes) {
            if (!isValidOtpCode(body, codeMatch.range.first, codeMatch.value)) continue

            // Check if code is near a keyword (within 80 chars after, 40 chars before)
            val nearKeyword = keywords.any { kw ->
                val afterKeywordDistance = codeMatch.range.first - kw.last
                val beforeKeywordDistance = kw.first - codeMatch.range.last
                (afterKeywordDistance in 0..80) || (beforeKeywordDistance in 0..40)
            }
            if (nearKeyword) return codeMatch.value
        }

        return null
    }

    /**
     * Strategy 2: Detect code-first patterns like "98094 is the authorization code"
     */
    private fun extractCodeFirstPattern(body: String): String? {
        val match = codeFirstPattern.find(body) ?: return null
        val code = match.groupValues[1]

        // Check for excluded code types in the match context
        val matchText = match.value.lowercase()
        if (excludedCodeTypes.any { matchText.contains(it) }) return null

        if (isValidOtpCode(body, match.range.first, code)) {
            return code
        }
        return null
    }

    /**
     * Strategy 3: Generic "code" patterns with context validation
     */
    private fun extractGenericCodePattern(body: String): String? {
        // Try "the/your code is [DIGITS]" pattern
        genericCodeContextPattern.find(body)?.let { match ->
            val code = match.groupValues[1]
            val matchText = match.value.lowercase()
            if (!excludedCodeTypes.any { matchText.contains(it) } &&
                isValidOtpCode(body, match.range.first, code)) {
                return code
            }
        }

        // Try "code for [service] is [DIGITS]" pattern
        codeForPattern.find(body)?.let { match ->
            val code = match.groupValues[1]
            val matchText = match.value.lowercase()
            if (!excludedCodeTypes.any { matchText.contains(it) } &&
                isValidOtpCode(body, match.range.first, code)) {
                return code
            }
        }

        return null
    }

    /**
     * Strategy 4: Use contextual indicators (time validity, do-not-share warnings)
     * Combined with digit proximity for lower-confidence but still useful detection
     */
    private fun extractWithContextualIndicators(body: String): String? {
        val hasTimeIndicator = timeValidityPattern.containsMatchIn(body)
        val hasDoNotShare = doNotSharePattern.containsMatchIn(body)

        // Need at least one strong contextual signal
        if (!hasTimeIndicator && !hasDoNotShare) return null

        // Find all digit sequences and pick the most likely OTP
        val codes = otpCodeRegex.findAll(body).toList()
        for (codeMatch in codes) {
            if (isValidOtpCode(body, codeMatch.range.first, codeMatch.value)) {
                return codeMatch.value
            }
        }

        return null
    }

    /**
     * Validate that a code is likely an OTP (not a monetary amount, phone number, etc.)
     */
    private fun isValidOtpCode(body: String, codeStart: Int, code: String): Boolean {
        // Check if preceded by currency indicator
        val lookbackStart = maxOf(0, codeStart - 10)
        val prefix = body.substring(lookbackStart, codeStart)
        if (monetaryPrefixRegex.containsMatchIn(prefix)) return false

        // Check if it looks like a phone number (10+ consecutive digits in context)
        val lookAround = body.substring(
            maxOf(0, codeStart - 5),
            minOf(body.length, codeStart + code.length + 5)
        )
        val digitCount = lookAround.count { it.isDigit() }
        if (digitCount > 10) return false

        return true
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
