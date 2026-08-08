package com.praveenpuglia.cleansms

import com.praveenpuglia.cleansms.ui.newmessage.SmsCounter
import com.praveenpuglia.cleansms.ui.newmessage.smsCounter
import org.junit.Assert.assertEquals
import org.junit.Test

class NewMessageSmsCounterTest {
    @Test
    fun countsGsmAndUnicodeBoundaries() {
        listOf(
            "" to SmsCounter("0 / 160"),
            "a".repeat(160) to SmsCounter("160 / 160", nearLimit = true),
            "a".repeat(161) to SmsCounter("145", "2 SMS"),
            "₹".repeat(70) to SmsCounter("70 / 70", nearLimit = true),
            "₹".repeat(71) to SmsCounter("63", "2 SMS"),
        ).forEach { (message, expected) -> assertEquals(expected, smsCounter(message)) }
    }
}
