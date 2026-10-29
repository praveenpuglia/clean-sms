package com.praveenpuglia.cleansms

import android.app.PendingIntent
import android.content.ContentValues
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(AndroidJUnit4::class)
class OtpCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    // Messages and "now" are set in 2001 so a cleanup can only ever reach this test's own messages,
    // never the rest of the device's inbox.
    private val now = 978_307_200_000L

    @Before
    fun setUp() {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        cleanUp()
    }

    @After
    fun cleanUp() {
        AppSettings.setOtpAutoDeleteAfter(context, Duration.ZERO, includeExisting = false)
        OtpCleanupReceiver.run(context)
        StarredMessages.ids(context).forEach { if (it in inserted) StarredMessages.toggle(context, it) }
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(SENDER))
        inserted.clear()
    }

    @Test
    fun offDeletesNothing() {
        val oldOtp = insert("Your OTP is 482913", now - 30 * DAY)

        OtpCleanupReceiver.run(context, now)

        assertEquals(1, count(oldOtp))
    }

    @Test
    fun deletesOnlyDueUnstarredOtpsAndWaitsForTheNextOne() {
        val dueOtp = insert("Your OTP is 482913", now - 2 * HOUR)
        val dueText = insert("Your order has shipped", now - 2 * HOUR)
        val starredOtp = insert("Your OTP is 771204", now - 2 * HOUR).also { StarredMessages.toggle(context, it) }
        val freshOtp = insert("Your OTP is 650318", now - 10 * MINUTE)
        assertEquals("count shown before turning on", 1, OtpCleanupReceiver.countDue(context, 1.hours, since = 0L, now = now))
        AppSettings.setOtpAutoDeleteAfter(context, 1.hours, includeExisting = true, now = now)

        OtpCleanupReceiver.run(context, now)

        assertEquals("due OTP", 0, count(dueOtp))
        assertEquals("not an OTP", 1, count(dueText))
        assertEquals("starred", 1, count(starredOtp))
        assertEquals("not due yet", 1, count(freshOtp))
        assertNotNull("alarm for the fresh OTP", OtpCleanupReceiver.alarmIntent(context, PendingIntent.FLAG_NO_CREATE))

        // When that alarm fires, the fresh one is due too.
        OtpCleanupReceiver.run(context, now + HOUR)
        assertEquals(0, count(freshOtp))
    }

    @Test
    fun turningItOffClearsTheAlarm() {
        insert("Your OTP is 650318", now - 10 * MINUTE)
        AppSettings.setOtpAutoDeleteAfter(context, 1.hours, includeExisting = true, now = now)
        OtpCleanupReceiver.run(context, now)
        assertNotNull(OtpCleanupReceiver.alarmIntent(context, PendingIntent.FLAG_NO_CREATE))

        AppSettings.setOtpAutoDeleteAfter(context, Duration.ZERO, includeExisting = false)
        OtpCleanupReceiver.run(context, now)

        assertNull(OtpCleanupReceiver.alarmIntent(context, PendingIntent.FLAG_NO_CREATE))
    }

    @Test
    fun newOtpsOnlyKeepsWhatIsAlreadyThereAndDeletesWhatArrivesLater() {
        val existingOld = insert("Your OTP is 482913", now - 2 * HOUR)
        val existingRecent = insert("Your OTP is 771204", now - 10 * MINUTE)
        AppSettings.setOtpAutoDeleteAfter(context, 1.hours, includeExisting = false, now = now)
        val arrivedLater = insert("Your OTP is 650318", now + MINUTE)

        OtpCleanupReceiver.run(context, now + 2 * HOUR)

        assertEquals(1, count(existingOld))
        assertEquals(1, count(existingRecent))
        assertEquals(0, count(arrivedLater))
    }

    @Test
    fun shorteningTheDurationKeepsTheChosenScope() {
        val existing = insert("Your OTP is 482913", now - 2 * HOUR)
        AppSettings.setOtpAutoDeleteAfter(context, 7.days, includeExisting = false, now = now)
        AppSettings.setOtpAutoDeleteAfter(context, 1.hours, includeExisting = true, now = now)

        OtpCleanupReceiver.run(context, now)

        assertEquals("still new OTPs only", 1, count(existing))
    }

    private val inserted = mutableSetOf<Long>()

    private fun insert(body: String, date: Long): Long {
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put(Telephony.Sms.ADDRESS, SENDER)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 1)
        })!!
        return uri.lastPathSegment!!.toLong().also { inserted += it }
    }

    private fun count(id: Long): Int =
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms._ID} = ?", arrayOf(id.toString()), null)!!
            .use { it.count }

    private companion object {
        const val SENDER = "VM-TSTOTP-S"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
