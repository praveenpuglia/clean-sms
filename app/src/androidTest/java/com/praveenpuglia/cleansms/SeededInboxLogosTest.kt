package com.praveenpuglia.cleansms

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Telephony
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end over the debug seed, whose variety mirrors a real Indian inbox: which senders get a
 * brand logo, which must not (look-alikes, government, unregistered, numeric, shortcodes), and
 * which messages land in the OTP tab.
 */
@RunWith(AndroidJUnit4::class)
class SeededInboxLogosTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun seed() {
        shell("cmd role add-role-holder android.app.role.SMS ${context.packageName} 0")
        AppSettings.setOnboardingCompleted(context)
        AppSettings.setShowSenderLogos(context, true)
        shell("am broadcast -n ${context.packageName}/.DebugSeedReceiver -a com.praveenpuglia.cleansms.DEBUG_SEED")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!seeded() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(200)
    }

    @Test
    fun brandsGetTheirLogoAndLookAlikesDoNot() {
        val expected = mapOf(
            "AX-AUBANK-T" to "au_bank", "VM-BAJAJF-S" to "bajaj_finance", "JD-BOBTXN-S" to "bank_of_baroda",
            "VM-BOIIND-S" to "bank_of_india", "AD-CANBNK-T" to "canara_bank", "VK-FEDBNK-S" to "federal_bank",
            "VM-IDFCFB-S" to "idfc_first", "JM-INDUSB-T" to "indusind", "VM-PNBSMS-S" to "pnb",
            "AD-UNIONB-S" to "union_bank", "VK-YESBNK-T" to "yes_bank", "VM-PAYTMB-S" to "paytm",
            "AX-PHONPE-S" to "phonepe", "VM-LICIND-S" to "lic", "JD-JIOINF-S" to "jio", "VM-VICARE-S" to "vi",
            "VM-Airtel-S" to "airtel", "JK-blnkit-S" to "blinkit", "VM-ZEPTON-S" to "zepto",
            "JM-SWIGGY-S" to "swiggy", "VK-BIGBKT-S" to "bigbasket", "BP-NYKAAA-P" to "nykaa",
            "VM-OLACAB-S" to "ola", "VM-IRCTCi-S" to "irctc", "VM-HDFCBK-T" to "hdfc_bank",
            "JM-ICICIB-T" to "icici_bank", "AD-SBIBNK-T" to "sbi", "VK-AXISBK-T" to "axis_bank",
            "VM-KOTAKB-T" to "kotak", "VM-INDIGO-S" to "indigo",
            // No logo: look-alikes, a paint company sharing IndiGo's name, government, CRIS (not IRCTC),
            // unregistered and numeric headers, shortcodes, and personal numbers.
            "VK-GOOGLE-T" to null, "BP-NETFLX-T" to null, "AD-Indigo-P" to null, "JZ-NDMAEW-G" to null,
            "VM-IRSMSa-G" to null, "AX-QWZXKP-S" to null, "VM-650018-P" to null, "57575" to null,
        )
        val threads = inboxThreads(expected.keys)
        expected.forEach { (address, brand) ->
            val uri = threads.getValue(address).contactPhotoUri
            assertEquals(address, brand?.let { "android.resource://${context.packageName}/drawable/brand_$it" }, uri)
        }
    }

    @Test
    fun otpTabShowsRealCodesAndSkipsLookAlikeNumbers() {
        val expected = mapOf(
            "VK-YESBNK-T" to "582913", // card txn OTP alongside an INR amount
            "VM-ZEPTON-S" to "8213", // delivery code
            "VM-OLACAB-S" to "4821", // ride OTP in a message with a date and time
            "VM-CDSLTX-S" to "736104", // code-first demat OTP
            "VM-IRCTCi-S" to null, // PNR and train number with a "never share OTP" footer
            "JM-SWIGGY-S" to null, // "never share OTP" footer only
            "57575" to null, // reference number + "we never ask for OTP"
            "AD-UNIONB-S" to null, // bank ref number + "never share OTP/PIN"
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var codes = emptyMap<String, String>()
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    codes = ViewModelProvider(activity)[InboxViewModel::class.java].otpMessages
                        .groupBy { it.address }.mapValues { it.value.first().otpCode }
                }
                if (expected.filterValues { it != null }.keys.all { it in codes }) break
                SystemClock.sleep(200)
            }
            expected.forEach { (address, code) -> assertEquals(address, code, codes[address]) }
        }
    }

    private fun inboxThreads(addresses: Set<String>): Map<String, ThreadItem> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var threads = emptyMap<String, ThreadItem>()
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    threads = ViewModelProvider(activity)[InboxViewModel::class.java].allThreads.associateBy { it.nameOrAddress }
                }
                if (addresses.all { it in threads }) break
                SystemClock.sleep(200)
            }
            return threads
        }
    }

    private fun seeded(): Boolean = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
        "${Telephony.Sms.ADDRESS} = ?", arrayOf("VM-CDSLTX-S"), null,
    )?.use { it.count > 0 } ?: false

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
