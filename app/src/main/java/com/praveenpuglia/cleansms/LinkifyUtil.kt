package com.praveenpuglia.cleansms

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.core.text.util.LinkifyCompat

/**
 * Applies linkification to a TextView for: web URLs, phone numbers, emails, map addresses.
 * Constitution §6 separation: keeps UI binding lean. §5 UX: makes actionable data tappable.
 * We rely on Android's built-in Linkify for reliability & locale coverage instead of manual regex sets.
 */
object LinkifyUtil {
    // Removed MAP_ADDRESSES (deprecated & OEM inconsistent). We keep primary actionable types.
    private const val MASK = Linkify.WEB_URLS or Linkify.PHONE_NUMBERS or Linkify.EMAIL_ADDRESSES
    private const val MAX_CACHE_ENTRIES = 128

    // Simple LRU cache for processed spannables (constitution §12 performance, avoid repeated regex passes)
    private val cache = object : LinkedHashMap<String, Spannable>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Spannable>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    // Context pattern to detect card last‑four references; if match found around a 4‑digit tel span, we remove that span.
    private val cardContextRegex = Regex("card\\s*(?:ending\\s*in\\s*)?(\\d{4})", RegexOption.IGNORE_CASE)

    fun linkify(tv: TextView) {
        val rawText = tv.text?.toString() ?: return
        if (rawText.isEmpty() || rawText.length > 8000) return
        // Cache hit: clone cached spannable to avoid sharing mutable spans across views
        val cached = cache[rawText]
        val spannable = if (cached != null) {
            SpannableString(cached) // defensive copy
        } else {
            val s = SpannableString(rawText)
            try {
                if (!LinkifyCompat.addLinks(s, MASK)) {
                    Linkify.addLinks(s, MASK)
                }
            } catch (e: Throwable) {
                android.util.Log.w("LinkifyUtil", "Linkify failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            // Remove false positive phone spans that are actually card last-4 references
            stripCardLastFourPhoneSpans(s, rawText)
            cache[rawText] = s
            s
        }
        tv.text = spannable
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
    }

    private fun stripCardLastFourPhoneSpans(spannable: Spannable, fullText: String) {
        val spans = spannable.getSpans(0, spannable.length, android.text.style.URLSpan::class.java)
        for (span in spans) {
            val url = span.url ?: continue
            if (!url.startsWith("tel:")) continue
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start < 0 || end <= start || end > spannable.length) continue
            val numberText = fullText.substring(start, end)
            // Only care about 4-digit numbers (typical card last-4) to reduce accidental removals
            if (numberText.length == 4 && numberText.all { it.isDigit() }) {
                val contextStart = (start - 25).coerceAtLeast(0)
                val contextEnd = (end + 25).coerceAtMost(fullText.length)
                val contextSlice = fullText.substring(contextStart, contextEnd)
                if (cardContextRegex.containsMatchIn(contextSlice)) {
                    spannable.removeSpan(span)
                }
            }
        }
    }
}
