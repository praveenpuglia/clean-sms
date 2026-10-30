package com.praveenpuglia.cleansms

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Carrier status reports for sent messages: read, recorded on the row, and shown in the thread. */
@RunWith(AndroidJUnit4::class)
class DeliveryReportTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        context.getSharedPreferences("CleanSmsPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarding_completed", true).commit()
        cleanUp()
    }

    @After
    fun cleanUp() {
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(TO))
    }

    @Test
    fun statusReportsMapToDeliveredPendingAndFailed() {
        assertEquals(Telephony.Sms.STATUS_COMPLETE, SmsSentReceiver.deliveryStatus(report(0x00)))
        assertEquals(Telephony.Sms.STATUS_PENDING, SmsSentReceiver.deliveryStatus(report(0x20)))
        assertEquals(Telephony.Sms.STATUS_FAILED, SmsSentReceiver.deliveryStatus(report(0x40)))
        // The carrier gave up retrying: also not delivered.
        assertEquals(Telephony.Sms.STATUS_FAILED, SmsSentReceiver.deliveryStatus(report(0x60)))
        assertEquals(null, SmsSentReceiver.deliveryStatus(Intent(SmsSentReceiver.ACTION_SMS_DELIVERED)))
    }

    @Test
    fun deliveredReportShowsADoubleTick() {
        val row = insertSent("See you at 6")
        receive(row, 0x00)

        assertEquals(Telephony.Sms.STATUS_COMPLETE, statusOf(row))
        openThread(row) {
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithContentDescription("Delivered", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun failedReportShowsNotDeliveredRatherThanNotSent() {
        val row = insertSent("Are you there?")
        receive(row, 0x40)

        assertEquals(Telephony.Sms.STATUS_FAILED, statusOf(row))
        openThread(row) {
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText("Not delivered", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(0, composeRule.onAllNodesWithText("Not sent", useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    private fun receive(row: Uri, tpStatus: Int) {
        SmsSentReceiver().onReceive(context, report(tpStatus).setData(row))
    }

    /** A 3GPP SMS-STATUS-REPORT PDU, delivered the way the radio hands it to the PendingIntent. */
    private fun report(tpStatus: Int): Intent {
        val hex = "00" + // no SMSC address
            "06" + // SMS-STATUS-REPORT
            "01" + // message reference
            "0A91" + "2143658709" + // recipient +1234567890
            "62103041000000" + // service centre time stamp
            "62103041000100" + // discharge time
            "%02X".format(tpStatus)
        val pdu = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return Intent(SmsSentReceiver.ACTION_SMS_DELIVERED)
            .putExtra("pdu", pdu)
            .putExtra("format", SmsMessage.FORMAT_3GPP)
    }

    private fun insertSent(body: String): Uri = context.contentResolver.insert(
        Telephony.Sms.CONTENT_URI,
        ContentValues().apply {
            put(Telephony.Sms.ADDRESS, TO)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(context, setOf(TO)))
        },
    )!!

    private fun statusOf(row: Uri): Int =
        context.contentResolver.query(row, arrayOf(Telephony.Sms.STATUS), null, null, null)!!
            .use { check(it.moveToFirst()); it.getInt(0) }

    private fun threadOf(row: Uri): Long =
        context.contentResolver.query(row, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)!!
            .use { check(it.moveToFirst()); it.getLong(0) }

    private fun openThread(row: Uri, block: () -> Unit) {
        ActivityScenario.launch<ThreadDetailActivity>(
            ThreadDetailActivity.intent(context, threadOf(row), TO, null, null, null, MessageCategory.PERSONAL),
        ).use { block() }
    }

    private companion object {
        const val TO = "+919000012345"
    }
}
