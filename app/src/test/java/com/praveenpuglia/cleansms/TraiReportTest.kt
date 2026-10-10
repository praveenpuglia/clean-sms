package com.praveenpuglia.cleansms

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraiReportTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test
    fun complaintIsTheMessageTextThenSenderThenLocalDate() {
        // 23:30 IST on 9 Oct is still 9 Oct here, though it is 18:00 UTC
        val received = ZonedDateTime.of(2026, 10, 9, 23, 30, 0, 0, ist).toInstant().toEpochMilli()
        assertEquals(
            "Get a loan of Rs.5,00,000 in 10 mins. Apply: loan.example, VM-ABCDEF-P, 09/10/26",
            TraiReport.complaint("Get a loan of Rs.5,00,000 in 10 mins. Apply: loan.example", " VM-ABCDEF-P ", received, ist),
        )
        // Operator label dropped, line breaks flattened
        assertEquals(
            "Earn Rs.5,000 a day from home. Join now, +919876543210, 09/10/26",
            TraiReport.complaint("Jio Alert : SPAM Earn Rs.5,000 a day\nfrom home.\n\nJoin now", "+919876543210", received, ist),
        )
    }

    @Test
    fun allowsReportsOnlyWithinSevenDays() {
        val now = 1_760_000_000_000L
        val day = TimeUnit.DAYS.toMillis(1)
        assertTrue(TraiReport.canReport(now, now))
        assertTrue(TraiReport.canReport(now - 7 * day, now))
        assertFalse(TraiReport.canReport(now - 7 * day - 1, now))
        assertFalse(TraiReport.canReport(now + day, now)) // clock skew: a "future" message isn't reportable
    }
}
