package com.praveenpuglia.cleansms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamDetectorTest {
    @Test
    fun recognizesOperatorSpamLabelsAndRemovesThemFromDisplayText() {
        listOf(
            "  Airtel Warning: SPAM | Suspicious investment offer  ",
            "Airtel Warning: SPAM|Suspicious investment offer",
            "Jio Alert : SPAM Suspicious investment offer",
            "Jio Alert: SPAM | Suspicious investment offer",
            "JIO ALERT : SPAM - Suspicious investment offer",
        ).forEach { spam ->
            assertTrue(spam, SpamDetector.isSpam(spam))
            assertEquals(spam, "Suspicious investment offer", SpamDetector.getCleanBody(spam).trim())
        }
    }

    @Test
    fun ignoresLabelsThatAreNotTheOperatorPrefix() {
        assertFalse(SpamDetector.isSpam("Forwarded Airtel Warning: SPAM | offer"))
        assertFalse(SpamDetector.isSpam("Jio Alert: Your plan expires tomorrow. Recharge now."))
        assertFalse(SpamDetector.isSpam("Jio Alert : SPAMMY deals inside")) // a word, not the label
        assertEquals("Regular message", SpamDetector.getCleanBody("Regular message"))
        assertEquals("", SpamDetector.getCleanBody(null))
    }
}
