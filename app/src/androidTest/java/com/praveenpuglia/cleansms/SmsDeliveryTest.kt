package com.praveenpuglia.cleansms

import android.app.NotificationManager
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

@RunWith(AndroidJUnit4::class)
class SmsDeliveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        notifications.cancelAll()
        cleanUp()
    }

    @After
    fun cleanUp() {
        AppSettings.setPromoNotificationsEnabled(context, true)
        notifications.cancelAll()
        ADDRESSES.forEach { address ->
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(address))
        }
    }

    @Test
    fun otpIsStoredUnreadAndPostedOnTheOtpChannel() {
        SmsDeliverReceiver.deliver(context, OTP_SENDER, "Your OTP is 482913. Do not share it.")

        assertEquals(0, storedReadFlag(OTP_SENDER, "Your OTP is 482913. Do not share it."))
        val posted = postedFor(OTP_SENDER)
        assertNotNull(posted)
        assertEquals(SmsDeliverReceiver.CHANNEL_OTP, posted!!.notification.channelId)
    }

    @Test
    fun personalMessageIsPostedOnTheRegularChannel() {
        SmsDeliverReceiver.deliver(context, PERSONAL_SENDER, "See you at 7")

        assertEquals(0, storedReadFlag(PERSONAL_SENDER, "See you at 7"))
        assertEquals(SmsDeliverReceiver.CHANNEL_SMS, postedFor(PERSONAL_SENDER)?.notification?.channelId)
    }

    @Test
    fun mutedPromotionsAreStoredButNotNotifiedWhileOtpsStillAre() {
        AppSettings.setPromoNotificationsEnabled(context, false)

        SmsDeliverReceiver.deliver(context, PROMO_SENDER, "Flat 50% off this weekend only")
        assertEquals(0, storedReadFlag(PROMO_SENDER, "Flat 50% off this weekend only"))
        assertNull(postedFor(PROMO_SENDER))

        SmsDeliverReceiver.deliver(context, PROMO_SENDER, "Your OTP is 771204 for checkout")
        assertEquals(SmsDeliverReceiver.CHANNEL_OTP, postedFor(PROMO_SENDER)?.notification?.channelId)
    }

    @Test
    fun notificationsNeverCarryBrandLogos() {
        AppSettings.setShowSenderLogos(context, true)
        SmsDeliverReceiver.deliver(context, BRAND_SENDER, "Your OTP is 517403 for checkout. Do not share it.")

        val posted = postedFor(BRAND_SENDER)
        assertNotNull(posted)
        assertNull("brand logo must not appear in notifications", posted!!.notification.getLargeIcon())
    }

    private fun storedReadFlag(address: String, body: String): Int? =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.READ),
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ?",
            arrayOf(address, body),
            null,
        )?.use { if (it.moveToFirst()) it.getInt(0) else null }

    /** notify() is enqueued asynchronously by the system, so give a posted notification a moment to appear. */
    private fun postedFor(address: String): android.service.notification.StatusBarNotification? {
        val deadline = android.os.SystemClock.uptimeMillis() + 2_000
        while (true) {
            notifications.activeNotifications.firstOrNull { it.id == address.hashCode() }?.let { return it }
            if (android.os.SystemClock.uptimeMillis() > deadline) return null
            android.os.SystemClock.sleep(100)
        }
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val OTP_SENDER = "+15550100001"
        const val PERSONAL_SENDER = "+15550100002"
        const val PROMO_SENDER = "VM-TSTSAL-P"
        const val BRAND_SENDER = "VK-YESBNK-T"
        val ADDRESSES = listOf(OTP_SENDER, PERSONAL_SENDER, PROMO_SENDER, BRAND_SENDER)
    }
}
