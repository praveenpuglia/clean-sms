package com.praveenpuglia.cleansms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamDetectorTest {
    @Test
    fun recognizesOnlyTheAirtelWarningPrefixAndRemovesItFromDisplayText() {
        val spam = "  Airtel Warning: SPAM | Suspicious investment offer  "

        assertTrue(SpamDetector.isSpam(spam))
        assertEquals("| Suspicious investment offer", SpamDetector.getCleanBody(spam))
        assertFalse(SpamDetector.isSpam("Forwarded Airtel Warning: SPAM | offer"))
        assertEquals("Regular message", SpamDetector.getCleanBody("Regular message"))
        assertEquals("", SpamDetector.getCleanBody(null))
    }
}
