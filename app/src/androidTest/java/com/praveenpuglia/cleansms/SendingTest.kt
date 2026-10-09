package com.praveenpuglia.cleansms

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Sends through the emulator modem and checks what the default SMS app records. */
@RunWith(AndroidJUnit4::class)
class SendingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        shell("pm grant ${context.packageName} android.permission.SEND_SMS")
        cleanUp()
    }

    @After
    fun cleanUp() {
        RECIPIENTS.forEach {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(it))
        }
    }

    @Test
    fun sentMessageIsRecordedOnceInTheSentBoxWithItsThread() {
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(SHORT_TO))
        SmsSender.send(context, SHORT_TO, "On my way", threadId = threadId)

        val rows = sentRows(SHORT_TO)
        assertEquals(listOf(SentRow("On my way", 1, threadId)), rows)
    }

    @Test
    fun longMessageIsSplitForSendingButRecordedAsOneMessage() {
        val body = "Long reply ".repeat(40).trim()
        SmsSender.send(context, LONG_TO, body)

        val rows = sentRows(LONG_TO)
        assertEquals(1, rows.size)
        assertEquals(body, rows.single().body)
        assertTrue(rows.single().threadId > 0)
    }

    @Test
    fun quickReplyFromThePhoneAppIsSentAndRecorded() {
        context.startService(
            Intent(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE, Uri.parse("smsto:$QUICK_REPLY_TO"))
                .setClass(context, RespondViaMessageService::class.java)
                .putExtra(Intent.EXTRA_TEXT, "Can't talk now"),
        )

        val deadline = SystemClock.uptimeMillis() + 5_000
        while (sentRows(QUICK_REPLY_TO).isEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        val row = sentRows(QUICK_REPLY_TO).singleOrNull()
        assertNotNull("quick reply was not recorded in the Sent box", row)
        assertEquals("Can't talk now", row!!.body)
    }

    private data class SentRow(val body: String, val read: Int, val threadId: Long)

    private fun sentRows(address: String): List<SentRow> =
        context.contentResolver.query(
            Telephony.Sms.Sent.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.READ, Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.ADDRESS} = ?",
            arrayOf(address),
            null,
        )!!.use { c ->
            buildList { while (c.moveToNext()) add(SentRow(c.getString(0), c.getInt(1), c.getLong(2))) }
        }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val SHORT_TO = "+15550100011"
        const val LONG_TO = "+15550100012"
        const val QUICK_REPLY_TO = "+15550100013"
        val RECIPIENTS = listOf(SHORT_TO, LONG_TO, QUICK_REPLY_TO)
    }
}
