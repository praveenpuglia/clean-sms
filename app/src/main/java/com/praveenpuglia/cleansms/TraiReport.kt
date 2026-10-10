package com.praveenpuglia.cleansms

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Spam complaints to TRAI's 1909 short code (TCCCPR 2018). The SMS goes from the number that got
 * the spam; since the February 2025 amendment that works for 7 days after receipt, with or without
 * DND registration. The operator replies with a complaint number.
 */
object TraiReport {
    const val SHORT_CODE = "1909"
    private val window = TimeUnit.DAYS.toMillis(7)
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yy")

    fun canReport(receivedAt: Long, now: Long = System.currentTimeMillis()): Boolean = now - receivedAt in 0..window

    /**
     * TRAI's format, not user-facing copy, so not in strings.xml: "<the UCC>, <number or header>, dd/mm/yy",
     * where the UCC is the message text itself. The operator's spam label is dropped and line breaks are
     * flattened so the complaint stays one line; a long one simply goes as a multipart SMS.
     */
    fun complaint(body: String, sender: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val text = SpamDetector.getCleanBody(body).replace(Regex("""\s+"""), " ").trim()
        return "$text, ${sender.trim()}, ${dateFormat.format(Instant.ofEpochMilli(receivedAt).atZone(zone))}"
    }
}
