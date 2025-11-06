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

    fun linkify(tv: TextView) {
        val rawText = tv.text?.toString() ?: return
        if (rawText.isEmpty() || rawText.length > 8000) return
        // Always create a fresh SpannableString to avoid ClassCast issues (SpannedString != Spannable)
        val spannable = SpannableString(rawText)
        tv.text = spannable
        try {
            if (!LinkifyCompat.addLinks(tv, MASK)) {
                // Fallback: legacy Linkify on the spannable instance
                Linkify.addLinks(spannable, MASK)
            }
        } catch (e: Throwable) {
            android.util.Log.w("LinkifyUtil", "Linkify failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
    }
}
