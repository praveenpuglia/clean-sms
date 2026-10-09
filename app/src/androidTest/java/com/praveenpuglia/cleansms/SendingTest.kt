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

        // Queued in the Outbox, then moved to Sent when the radio confirms.
        assertEquals(listOf(SentRow("On my way", 1, threadId)), awaitRows(SHORT_TO, Telephony.Sms.MESSAGE_TYPE_SENT))
        assertEquals(1, rowCount(SHORT_TO))
    }

    @Test
    fun longMessageIsSplitForSendingButRecordedAsOneMessage() {
        val body = "Long reply ".repeat(40).trim()
        SmsSender.send(context, LONG_TO, body)

        val rows = awaitRows(LONG_TO, Telephony.Sms.MESSAGE_TYPE_SENT)
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

        val row = awaitRows(QUICK_REPLY_TO, Telephony.Sms.MESSAGE_TYPE_SENT).singleOrNull()
        assertNotNull("quick reply was not recorded in the Sent box", row)
        assertEquals("Can't talk now", row!!.body)
    }

    @Test
    fun sendWithTheRadioOffIsMarkedFailed() {
        shell("cmd connectivity airplane-mode enable")
        try {
            SystemClock.sleep(2_000) // let the radio power down
            runCatching { SmsSender.send(context, FAILED_TO, "Will not go out") }
            assertEquals(listOf("Will not go out"), awaitRows(FAILED_TO, Telephony.Sms.MESSAGE_TYPE_FAILED).map { it.body })
        } finally {
            shell("cmd connectivity airplane-mode disable")
            SystemClock.sleep(3_000) // give the radio time to come back for later tests
        }
    }

    private data class SentRow(val body: String, val read: Int, val threadId: Long)

    private fun awaitRows(address: String, type: Int): List<SentRow> {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var rows = rowsOfType(address, type)
        while (rows.isEmpty() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            rows = rowsOfType(address, type)
        }
        return rows
    }

    private fun rowCount(address: String): Int =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms.ADDRESS} = ?", arrayOf(address), null)!!
            .use { it.count }

    private fun rowsOfType(address: String, type: Int): List<SentRow> =
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.READ, Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.TYPE} = ?",
            arrayOf(address, type.toString()),
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
        const val FAILED_TO = "+15550100014"
        val RECIPIENTS = listOf(SHORT_TO, LONG_TO, QUICK_REPLY_TO, FAILED_TO)
    }
}
