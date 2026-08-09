package com.praveenpuglia.cleansms

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Telephony
import android.text.style.URLSpan
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBoundaryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings by lazy { context.getSharedPreferences("CleanSmsPrefs", Context.MODE_PRIVATE) }
    private val categories by lazy { context.getSharedPreferences("thread_categories", Context.MODE_PRIVATE) }

    @Before
    fun clearOwnedPreferences() {
        settings.edit().clear().commit()
        categories.edit().clear().commit()
    }

    @After
    fun restoreStablePreferences() {
        settings.edit().clear().putBoolean("onboarding_completed", true).commit()
        SettingsActivity.setThemeMode(context, SettingsActivity.THEME_DARK)
        SettingsActivity.setFontFamily(context, SettingsActivity.FontFamily.SANS_SERIF)
        categories.edit().clear().commit()
        runCatching {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.SERVICE_CENTER} = ?",
                arrayOf(TEST_SMS_TAG),
            )
        }
    }

    @Test
    fun settingsDefaultsRoundTripAndLegacyTabNamesMigrate() {
        assertEquals(SettingsActivity.THEME_SYSTEM, SettingsActivity.getThemeMode(context))
        assertEquals(SettingsActivity.DefaultTab.OTP, SettingsActivity.getDefaultTab(context))
        assertEquals(SettingsActivity.FontFamily.SANS_SERIF, SettingsActivity.getFontFamily(context))
        assertTrue(SettingsActivity.getPromoNotificationsEnabled(context))
        assertFalse(SettingsActivity.getAllTabEnabled(context))

        SettingsActivity.setThemeMode(context, SettingsActivity.THEME_LIGHT)
        SettingsActivity.setFontFamily(context, SettingsActivity.FontFamily.MONOSPACE)
        SettingsActivity.setPromoNotificationsEnabled(context, false)
        SettingsActivity.setAllTabEnabled(context, true)
        assertEquals(SettingsActivity.THEME_LIGHT, SettingsActivity.getThemeMode(context))
        assertEquals(SettingsActivity.FontFamily.MONOSPACE, SettingsActivity.getFontFamily(context))
        assertFalse(SettingsActivity.getPromoNotificationsEnabled(context))
        assertTrue(SettingsActivity.getAllTabEnabled(context))

        mapOf(
            "OTPs" to SettingsActivity.DefaultTab.OTP,
            "Personal" to SettingsActivity.DefaultTab.PERSONAL,
            "Transactions" to SettingsActivity.DefaultTab.TRANSACTIONAL,
            "Services" to SettingsActivity.DefaultTab.SERVICE,
            "Promotions" to SettingsActivity.DefaultTab.PROMOTIONAL,
            "Governmental" to SettingsActivity.DefaultTab.GOVERNMENT,
            "All" to SettingsActivity.DefaultTab.ALL,
        ).forEach { (legacy, expected) ->
            settings.edit().putString("default_tab", legacy).commit()
            assertEquals(legacy, expected, SettingsActivity.getDefaultTab(context))
            assertEquals(expected.ordinal, settings.getInt("default_tab", -1))
        }

        settings.edit().putInt("default_tab", Int.MAX_VALUE).commit()
        assertEquals(SettingsActivity.DefaultTab.OTP, SettingsActivity.getDefaultTab(context))
    }

    @Test
    fun categoryCacheRejectsCorruptionAndInvalidatesOldVersions() {
        categories.edit().putString("thread_bad", "NOT_A_CATEGORY").commit()
        assertNull(CategoryStorage.getCategory(context, "bad"))

        assertEquals(
            MessageCategory.TRANSACTIONAL,
            CategoryStorage.getCategoryOrCompute(context, "VM-HDFCBK-T", 1),
        )
        assertEquals(MessageCategory.TRANSACTIONAL, CategoryStorage.getCategory(context, "VM-HDFCBK-T"))

        categories.edit()
            .putInt("categorization_version", 3)
            .putString("thread_+919876543210", MessageCategory.PROMOTIONAL.name)
            .commit()
        assertEquals(
            MessageCategory.PERSONAL,
            CategoryStorage.getCategoryOrCompute(context, "+919876543210", 2),
        )
        assertEquals(4, categories.getInt("categorization_version", 0))
    }

    @Test
    fun unknownSendersAreInferredFromTheirMessageContent() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        assertTrue(DefaultSmsHelper.isDefaultSmsApp(context))
        mapOf(
            "ALPHAOFFER" to ("Exclusive limited time offer: get 50% off this sale" to MessageCategory.PROMOTIONAL),
            "ALPHATXN" to ("Your payment transaction receipt and account balance" to MessageCategory.TRANSACTIONAL),
            "ALPHASVC" to ("Your subscription service was renewed; validity update" to MessageCategory.SERVICE),
            "ALPHAGOV" to ("Official government ministry tax department notice" to MessageCategory.GOVERNMENT),
        ).forEach { (address, fixture) ->
            context.contentResolver.insert(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, fixture.first)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    put(Telephony.Sms.SERVICE_CENTER, TEST_SMS_TAG)
                },
            )
            val threadId = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.SERVICE_CENTER} = ?",
                arrayOf(address, TEST_SMS_TAG),
                null,
            )!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getLong(0)
            }
            assertEquals(address, fixture.second, CategoryClassifier.inferCategoryFromContent(context, address, threadId))
        }
    }

    @Test
    fun linkificationKeepsActionsButNeverTurnsCardLastFourIntoAPhoneLink() {
        val body = "Email help@example.com, visit https://example.com, call 9876543210; card ending 4521."
        val first = LinkifyUtil.linkify(body)
        val second = LinkifyUtil.linkify(body)
        val urls = first.getSpans(0, first.length, URLSpan::class.java).map(URLSpan::getURL)

        assertTrue(urls.any { it.startsWith("mailto:") })
        assertTrue(urls.any { it.startsWith("http") })
        assertTrue(urls.any { it == "tel:9876543210" })
        assertFalse(urls.any { it == "tel:4521" })
        assertNotSame(first, second)
        assertTrue(LinkifyUtil.linkify("x".repeat(8_001)).getSpans(0, 8_001, URLSpan::class.java).isEmpty())
    }

    @Test
    fun otpCopyReceiverCopiesOnlyWhenTheOtpExtraExists() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("before", "before"))
                val receiver = OtpCopyReceiver()

                receiver.onReceive(activity, Intent(OtpCopyReceiver.ACTION_COPY_OTP))
                assertEquals("before", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

                receiver.onReceive(
                    activity,
                    Intent(OtpCopyReceiver.ACTION_COPY_OTP).putExtra(OtpCopyReceiver.EXTRA_OTP, "4011"),
                )
                assertEquals("4011", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            }
        }
    }

    @Test
    fun avatarColorsAreStableAndThemeResolved() {
        val first = AvatarColorResolver.resolve(context, "VK-GOOGLE-T")
        val second = AvatarColorResolver.resolve(context, "VK-GOOGLE-T")

        assertEquals(first, second)
        assertNotEquals(0, first.first)
        assertNotEquals(0, first.second)
    }

    @Test
    fun manifestKeepsTelephonyEntrypointsAndInternalOtpReceiverScopedCorrectly() {
        val flags = PackageManager.ComponentInfoFlags.of(0)
        val sms = context.packageManager.getReceiverInfo(ComponentName(context, SmsDeliverReceiver::class.java), flags)
        val wap = context.packageManager.getReceiverInfo(ComponentName(context, WapPushReceiver::class.java), flags)
        val copy = context.packageManager.getReceiverInfo(ComponentName(context, OtpCopyReceiver::class.java), flags)
        val respond = context.packageManager.getServiceInfo(ComponentName(context, RespondViaMessageService::class.java), flags)
        val stats = context.packageManager.getActivityInfo(ComponentName(context, StatsActivity::class.java), flags)

        assertTrue(sms.exported)
        assertEquals("android.permission.BROADCAST_SMS", sms.permission)
        assertTrue(wap.exported)
        assertEquals("android.permission.BROADCAST_WAP_PUSH", wap.permission)
        assertFalse(copy.exported)
        assertFalse(stats.exported)
        assertTrue(respond.exported)
        assertEquals("android.permission.SEND_RESPOND_VIA_MESSAGE", respond.permission)

        val sendTo = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:9988776655")).setPackage(context.packageName)
        assertTrue(
            context.packageManager.queryIntentActivities(
                sendTo,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ).isNotEmpty(),
        )
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private companion object {
        const val TEST_SMS_TAG = "CLEAN_SMS_ANDROID_TEST"
    }
}
