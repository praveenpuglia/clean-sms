package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.Telephony
import android.util.Log
import android.widget.Toast

/**
 * Debug-only receiver that seeds the SMS inbox with a curated set of test messages
 * covering all TRAI categories, OTP detection patterns, and edge cases.
 *
 * Trigger:
 *   adb shell am broadcast -a com.praveenpuglia.cleansms.DEBUG_SEED
 * Optional extras:
 *   --ez clear true      // wipe previously-seeded test rows first (default: true)
 *   --ez seed false      // clear without inserting a new seed set
 *   --ei count 5000      // generate a large inbox across 500 threads (max: 5,000)
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive: action=${intent.action}")
        if (intent.action != ACTION_DEBUG_SEED) return

        val pendingResult = goAsync()
        Thread({
            try {
                seed(context.applicationContext, Intent(intent))
            } catch (e: RuntimeException) {
                Log.e(TAG, "Seed failed", e)
            } finally {
                pendingResult.finish()
            }
        }, "DebugSmsSeeder").start()
    }

    private fun seed(context: Context, intent: Intent) {
        val performanceCount = intent.getIntExtra(EXTRA_COUNT, 0)
        if (intent.hasExtra(EXTRA_COUNT) && performanceCount !in 1..MAX_SEED_COUNT) {
            val summary = "count must be between 1 and $MAX_SEED_COUNT"
            Log.w(TAG, summary)
            showToast(context, summary)
            return
        }

        val shouldClear = intent.getBooleanExtra(EXTRA_CLEAR, true)
        val resolver = context.contentResolver

        var deleted = 0
        if (shouldClear) {
            try {
                deleted = resolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "service_center = ?",
                    arrayOf(SEED_TAG)
                )
                Log.i(TAG, "Cleared $deleted previously seeded rows")
            } catch (e: Exception) {
                Log.e(TAG, "Clear failed", e)
            }
        }

        if (!intent.getBooleanExtra(EXTRA_SEED, true)) {
            val summary = "Cleared $deleted seeded msgs"
            Log.i(TAG, summary)
            showToast(context, summary)
            return
        }

        val now = System.currentTimeMillis()
        val messages = if (performanceCount == 0) {
            SEED_MESSAGES
        } else {
            List(performanceCount) { performanceSeed(it) }
        }
        var inserted = 0
        for (batch in messages.chunked(INSERT_BATCH_SIZE)) {
            val values = batch.map { it.toContentValues(now) }.toTypedArray()
            try {
                inserted += resolver.bulkInsert(Telephony.Sms.CONTENT_URI, values)
            } catch (e: Exception) {
                Log.e(TAG, "Batch insert failed", e)
            }
        }

        val contacts = if (performanceCount == 0) seedContacts(context) else 0

        val summary = buildString {
            append("Seeded $inserted msgs")
            if (deleted > 0) append(" (cleared $deleted)")
            if (contacts > 0) append(", $contacts contacts")
        }
        Log.i(TAG, summary)
        showToast(context, summary)
    }

    private fun showToast(context: Context, message: String) {
        Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Seed a small set of contacts so the Personal tab shows friendly names instead of raw numbers.
     * Idempotent: skips numbers that already have a contact (so re-seeding the inbox doesn't keep
     * adding duplicates). Requires WRITE_CONTACTS, granted via the debug manifest + adb pm grant.
     */
    private fun seedContacts(context: Context): Int {
        val resolver = context.contentResolver
        var added = 0
        for ((name, number) in SEED_CONTACTS) {
            if (contactExistsForNumber(context, number)) continue

            val ops = arrayListOf<ContentProviderOperation>(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, number)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()
            )
            try {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                added++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add contact $name <$number>", e)
            }
        }
        return added
    }

    private fun contactExistsForNumber(context: Context, number: String): Boolean {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        return context.contentResolver
            .query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }

    private data class Seed(
        val address: String,
        val body: String,
        val ageMinutes: Long,
        val read: Boolean = false,
        val type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
    )

    private fun Seed.toContentValues(now: Long) = ContentValues().apply {
        put(Telephony.Sms.ADDRESS, address)
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, now - ageMinutes * 60_000L)
        put(Telephony.Sms.DATE_SENT, now - ageMinutes * 60_000L)
        put(Telephony.Sms.READ, if (read) 1 else 0)
        put(Telephony.Sms.SEEN, if (read) 1 else 0)
        put(Telephony.Sms.TYPE, type)
        put(Telephony.Sms.SERVICE_CENTER, SEED_TAG)
    }

    private fun performanceSeed(index: Int): Seed {
        val threadIndex = index / PERFORMANCE_MESSAGES_PER_THREAD
        val template = SEED_MESSAGES[threadIndex % SEED_MESSAGES.size]
        val suffix = template.address.substringAfterLast('-')
            .takeIf { it.length == 1 }
            ?: "S"
        val address = if (template.address.startsWith('+')) {
            "+91${9_000_000_000L + threadIndex}"
        } else {
            "VM-P${threadIndex.toString().padStart(5, '0')}-$suffix"
        }
        return template.copy(
            address = address,
            ageMinutes = index.toLong(),
            read = template.read || index % 5 != 0,
        )
    }

    companion object {
        private const val TAG = "DebugSeedReceiver"
        const val ACTION_DEBUG_SEED = "com.praveenpuglia.cleansms.DEBUG_SEED"
        const val EXTRA_CLEAR = "clear"
        const val EXTRA_SEED = "seed"
        const val EXTRA_COUNT = "count"

        private const val MAX_SEED_COUNT = 5_000
        private const val PERFORMANCE_MESSAGES_PER_THREAD = 10
        private const val INSERT_BATCH_SIZE = 1_000

        // Sentinel stored in `service_center` so we can find & wipe our seeded rows.
        // Real SMSCs are short numbers like "+919885005444"; this string is harmless if it leaks.
        private const val SEED_TAG = "CLEAN_SMS_DEBUG_SEED"

        // Friendly names for the personal-thread phone numbers used above.
        // Used only so the Personal tab shows recognizable names in screenshots.
        private val SEED_CONTACTS = listOf(
            "Mom" to "+919876543210",
            "Anita" to "+918765432109",
            "Dad" to "+917654321098",
            "Riya Sharma" to "+919988776655",
            "Vikram (Boss)" to "+918877665544",
            "Priya Iyer" to "+919876512345",
            "Aditi" to "+919812345678",
        )

        // ageMinutes is "minutes ago"; spread across ~14 days for thread/list realism.
        private val SEED_MESSAGES = listOf(
            // ===== TRANSACTIONAL — banking debits/credits =====
            Seed("VM-HDFCBK-T",
                "Rs.4,500.00 debited from A/c XX1234 on 19-May-26 to UPI/john@okhdfcbank. Avl Bal: Rs.27,840.50. Not you? Call 18002586161. -HDFC Bank",
                ageMinutes = 25),
            Seed("VM-HDFCBK-T",
                "Rs.1,250.00 credited to A/c XX1234 by NEFT from RAVI KUMAR on 19-May-26 13:42. Bal: Rs.29,090.50. -HDFC Bank",
                ageMinutes = 180),
            Seed("JM-ICICIB-T",
                "ICICI Bank: Rs.850 debited from Acct XX9876 by Card XX4521 at SWIGGY on 19-May-26. Bal: Rs.45,890. SMS BLOCK 4521 to 9215676766 if not you.",
                ageMinutes = 320),
            Seed("AD-SBIBNK-T",
                "Dear Customer, your A/c XXXXXX5678 has been debited with INR 12,500.00 on 18May26 by transfer to ELECTRICITY BD MAH. Avl Bal INR 33,420.55 -SBI",
                ageMinutes = 1480, read = true),
            Seed("VK-AXISBK-T",
                "OTP for txn of Rs.2,500 to AMAZON on card ending 4521 is 458291. Valid for 5 min. Do not share. -Axis Bank",
                ageMinutes = 12),
            Seed("VM-KOTAKB-T",
                "Dear Customer, your Kotak Credit Card ending 8821 has been used for Rs.1,899 at BIGBASKET on 19-May. If not you, block via Kotak811.",
                ageMinutes = 95),
            Seed("BZ-PAYTM-T",
                "Paytm: Rs.299 paid to SWIGGY on 19-May-26 12.30 PM. Wallet bal: Rs.450.25. Txn ID 7392845102.",
                ageMinutes = 240),
            Seed("BZ-PAYTM-T",
                "Paytm: You received Rs.500 from MOM via UPI on 18-May. Balance Rs.949.25. -Paytm Payments Bank",
                ageMinutes = 1620, read = true),

            // ===== TRANSACTIONAL — OTPs (varied detection patterns) =====
            // Strategy 1: explicit "OTP is" keyword
            Seed("VK-GOOGLE-T",
                "G-892341 is your Google verification code. Don't share it with anyone.",
                ageMinutes = 5),
            // Strategy 2: code-first ("XXXX is the authorization code")
            Seed("DZ-AMAZN-T",
                "743829 is the OTP to login to your Amazon account. Never share this OTP with anyone. Amazon will never call you to verify it.",
                ageMinutes = 35),
            // Strategy 2 variant: "is the authorization code"
            Seed("VM-MEDPLS-T",
                "478291 is the authorization code for your Medplus login. Please use it within 10 minutes. -Medplus",
                ageMinutes = 78),
            // Strategy 3: generic "code" with context
            Seed("BP-FLPKRT-T",
                "The verification code for your Flipkart login is 234567. Do not share with anyone. Valid for 10 mins.",
                ageMinutes = 145),
            // Strategy 4: time-validity + do-not-share but no explicit keyword
            Seed("JX-UBERIN-T",
                "4827 — your Uber code. Use within 5 minutes. Never share this code.",
                ageMinutes = 210),
            // OTP with monetary amount (must still detect 873920, not the Rs.2500)
            Seed("VM-AXISBK-T",
                "OTP 873920 for txn of Rs.2,500 at MYNTRA on card XX4521. Valid 5 min. Do not share. -Axis Bank",
                ageMinutes = 290),
            // 4-digit OTP
            Seed("BP-NETFLX-T",
                "Your Netflix sign-in code is 4729. Code expires in 15 minutes.",
                ageMinutes = 410, read = true),
            // 8-digit verification code
            Seed("VG-WHATSAPP-T",
                "Your WhatsApp code: 539-281. You can also tap this link to verify your phone: v.whatsapp.com/539281. Don't share this code with others.",
                ageMinutes = 540, read = true),
            // OTP via "use X as your" pattern
            Seed("VM-MSACCT-T",
                "Use 384921 as the Microsoft account security code. Do not share this code with anyone. Microsoft will never ask for it.",
                ageMinutes = 720, read = true),
            Seed("JD-SHDFAX-T",
                "Delivery Code: Share Pin 3693 with rider to accept delivery of Namma QE Bread.. order from Meesho. Get help @ https://lnk.shadowfax.in/SFXFWD/d0fl95ps -Shadowfax",
                ageMinutes = 12),
            Seed("VM-ZETA-T",
                "The OTP for your transaction amount of Rs. 1325 at RATNADEEP is 4011. - Zeta",
                ageMinutes = 18),
            Seed("VM-ZETA-T",
                "For a transaction of Rs. 9876 at RATNADEEP, use OTP 4011. - Zeta",
                ageMinutes = 22),

            // ===== PROMOTIONAL — e-commerce, food, travel =====
            Seed("DZ-AMAZN-P",
                "SUMMER SALE is LIVE! Up to 70% off on fashion, 50% on electronics. Use code SUMMER70. Shop now: amzn.in/x9k2t. T&C apply.",
                ageMinutes = 60),
            Seed("JK-MYNTRA-P",
                "Hi! End of Reason Sale starts at midnight. Min 40-80% OFF on top brands. Set a reminder: myntra.com/eors",
                ageMinutes = 140),
            Seed("BP-ZOMATO-P",
                "Hungry? Get 60% OFF on your next order (up to Rs.120). Use code WEEKEND60. Order on Zomato. Hurry — expires tonight!",
                ageMinutes = 250),
            Seed("JX-DOMINO-P",
                "FLAT 50% OFF on Pizzas! Min order Rs.499. Use code: PIZZA50. Valid till 31-May. Order now: dominos.in",
                ageMinutes = 800, read = true),
            Seed("VK-SWGGY-P",
                "Tired of cooking? Get 50% off + FREE delivery on your first order. Code SWIGGY50. Try Swiggy today!",
                ageMinutes = 1200, read = true),
            Seed("BZ-MMTRIP-P",
                "Summer travel deals! FLAT 25% OFF on flights + hotels. Use code SUMMER. Book by 31-May. -MakeMyTrip",
                ageMinutes = 1800, read = true),
            Seed("DZ-BIGBSK-P",
                "WEEKEND SALE! Flat 20% off on fruits & veggies. Min Rs.500. Code FRESH20. Order at bigbasket.com",
                ageMinutes = 2700, read = true),
            Seed("VM-TATCLQ-P",
                "Just landed: New arrivals from Westside, Zudio, and Tata CLiQ Luxury. Up to 60% off. Shop the latest: tatacliq.com",
                ageMinutes = 4320, read = true),
            Seed("BP-CRED-P",
                "You've earned 5,000 CRED coins this month. Redeem on premium offers — Apple, Nike, IKEA & more. Tap to claim.",
                ageMinutes = 5700, read = true),

            // ===== SERVICE — subscriptions, telecom, deliveries =====
            Seed("VM-AIRTEL-S",
                "Your prepaid plan of Rs.299 expires on 22-May-26. Recharge to continue enjoying unlimited calls and 1.5GB/day. -Airtel",
                ageMinutes = 40),
            Seed("JK-JIO-S",
                "Your Jio number has been recharged with Rs.349. Validity: 28 days. Data: 2GB/day. Calls: Unlimited. -Jio",
                ageMinutes = 220),
            Seed("VK-BSNL-S",
                "Dear customer, your BSNL bill of Rs.499 for May-26 is generated. Due date 25-May-26. Pay at portal.bsnl.in",
                ageMinutes = 600, read = true),
            Seed("BZ-VI-S",
                "Your Vi data usage has crossed 80% of your monthly limit. 200MB left. Recharge for additional data. -Vi",
                ageMinutes = 1080, read = true),
            Seed("JM-NETFLX-S",
                "Your Netflix subscription renewed for Rs.499. Next billing date: 19-Jun-26. Manage at netflix.com/account",
                ageMinutes = 2000, read = true),
            Seed("VM-IRSMSa-G",
                """
                    PNR-6503906054
                    Trn:13017
                    Dt:22-05-26 Dep.Time-06:05 Hrs.
                    Frm HWH to SNT
                    Cls:SL
                    P1-S1,57
                    Boarding allowed from HWH only
                    Chart Prepared
                    Url for coach position: https://enquiry.indianrail.gov.in/mntes/C?u=fheflNv4vNggejgkN8f
                    Download RailOne for latest updates https://railone.indianrailways.gov.in
                    For Enquiry/Complaint/Assistance, please dial 139 IR-CRIS""".trimIndent(),
                ageMinutes = 3000, read = true),
            Seed("VM-INDIGO-S",
                "Your Indigo flight 6E-234 BLR->DEL on 25-May dep 07.30 is on time. Web check-in open at goindigo.in/webcheckin",
                ageMinutes = 4500, read = true),
            Seed("BP-ZMATO-S",
                "Your order #7283910 from Burger King has been delivered. Rate your experience on the Zomato app.",
                ageMinutes = 5400, read = true),
            Seed("VK-BLINKT-S",
                "Your Blinkit order #BL839201 with 7 items has been delivered. Hope you enjoyed our 10-min delivery!",
                ageMinutes = 7200, read = true),
            Seed("DZ-SPOTFY-S",
                "Your Spotify Premium has been renewed for Rs.119. Next renewal 19-Jun-26. Enjoy ad-free music.",
                ageMinutes = 9800, read = true),

            // ===== GOVERNMENT =====
            Seed("BK-UIDAI-G",
                "OTP for your Aadhaar authentication is 482931. Valid for 10 minutes. Do not share with anyone. -UIDAI",
                ageMinutes = 50),
            Seed("VK-INCTAX-G",
                "Income Tax Dept: Your ITR for AY 2025-26 has been successfully processed. Refund of Rs.5,420 has been issued to bank a/c XX1234. -ITDept",
                ageMinutes = 1900, read = true),
            Seed("BZ-EPFOIN-G",
                "EPFO: Rs.8,500 credited to your PF account UAN XXXXX7890. Balance: Rs.4,52,830. Check passbook on epfindia.gov.in",
                ageMinutes = 4100, read = true),
            Seed("JM-MYGOV-G",
                "Voter ID update for EPIC ABC1234567: Your application has been approved. Card will be delivered within 30 days. -ECI",
                ageMinutes = 8600, read = true),
            Seed("VM-MEAGOV-G",
                "Passport application APN1234567 has been received. Visit your nearest PSK on 23-May-26 at 11.00 AM for verification. -MEA",
                ageMinutes = 11000, read = true),

            // ===== PERSONAL (full phone numbers) =====
            Seed("+919876543210",
                "Beta, can you pick up some milk on the way home? Thanks!",
                ageMinutes = 15),
            Seed("+919876543210",
                "Also, please get 1 kg sugar.",
                ageMinutes = 14),
            Seed("+918765432109",
                "Hey, are we still on for lunch tomorrow at 1pm at Indian Coffee House?",
                ageMinutes = 200),
            Seed("+918765432109",
                "Yes! See you there.",
                ageMinutes = 195, type = Telephony.Sms.MESSAGE_TYPE_SENT, read = true),
            Seed("+917654321098",
                "Just landed at the airport. Coming home in 30 mins.",
                ageMinutes = 800, read = true),
            Seed("+919988776655",
                "Happy birthday Praveen! Have a great day ahead.",
                ageMinutes = 2400, read = true),
            Seed("+918877665544",
                "Conference call at 3pm IST today. Please join: meet.google.com/abc-defg-hij",
                ageMinutes = 320, read = true),
            Seed("+919876512345",
                "Can you send me the quarterly report by EOD?",
                ageMinutes = 600, read = true),
            Seed("+919812345678",
                "Diwali greetings! Wishing you and your family a prosperous year ahead.",
                ageMinutes = 13000, read = true),

            // ===== UNKNOWN — inferable from content =====
            // No suffix, classifier should infer category from body keywords
            Seed("VM-CLEAR",
                "Your monthly subscription has been activated. Validity: 30 days. Manage your plan at the app.",
                ageMinutes = 1500, read = true),
            // Spammy promotional, no suffix
            Seed("BZ-LOTTRY",
                "Congratulations! You have won Rs.5 Lakh in the monthly lucky draw. Reply YES to claim your prize today!",
                ageMinutes = 4800, read = true),

            // ===== Airtel SPAM prefix (tests SpamDetector) =====
            Seed("+911140404040",
                "Airtel Warning: SPAM|Get a personal loan up to Rs.10 Lakh with no documents. Call 9999988888 now!",
                ageMinutes = 7500, read = true),

            // ===== Edge cases for OTP false-positive guarding =====
            // Order number (NOT an OTP — excluded by codeForPattern)
            Seed("BP-FLPKRT-S",
                "Your order 7283910 worth Rs.1,499 will be delivered by 5pm today. Track at flipkart.com/track/7283910",
                ageMinutes = 380, read = true),
            // PNR-like 10 digit (NOT an OTP — too long and excluded)
            Seed("DZ-IRCTC-S",
                "PNR 8472938102: Your train ticket has been confirmed. Coach S6, Seat 24, Lower Berth. -IRCTC",
                ageMinutes = 3200, read = true),
            // Monetary amount only (NOT an OTP)
            Seed("VM-HDFCBK-T",
                "Rs.50,000 has been credited to your account XX1234 by NEFT on 17-May-26. Bal: Rs.79,840. -HDFC Bank",
                ageMinutes = 3600, read = true),

            // ===== Real-world variety (synthetic text; headers are public DLT registry IDs) =====
            // One message per brand that has a bundled logo, so every logo can be eyeballed in the inbox.
            Seed("AX-AUBANK-T", "Rs.2,150.00 spent on your AU Bank Debit Card XX3307 at CITY MART on 08-10-26. Avl bal Rs.18,402.11. Not you? Call 1800 1200 1200 -AU Bank", ageMinutes = 33),
            Seed("VM-BAJAJF-S", "Dear Customer, EMI of Rs.3,499 for your Bajaj Finance loan A/c XX5520 is due on 12-10-26. Keep sufficient balance to avoid charges.", ageMinutes = 400, read = true),
            Seed("JD-BOBTXN-S", "Rs.780.00 transferred from A/c ...6612 to VPA asha.m@acmebank (UPI Ref No 628104773921). Not you? Call 18005700 -BOB", ageMinutes = 620, read = true),
            Seed("VM-BOIIND-S", "BOI: Your A/c XX0041 is credited with INR 15,000.00 on 07-10-26 by NEFT from ACME PAYROLL. Avl Bal INR 41,227.30", ageMinutes = 2900, read = true),
            Seed("AD-CANBNK-T", "An amount of INR 499.00 has been DEBITED to your account XXX2219 on 06/10/2026 towards Acme Streaming autopay. Total Avail.bal INR 9,120.44 - Canara Bank", ageMinutes = 4400, read = true),
            Seed("VK-FEDBNK-S", "Your Federal Bank Credit Card ending 8814 statement is generated. Total due Rs.12,480.00, minimum due Rs.624.00. Pay by 15-10-2026 to avoid charges.", ageMinutes = 1100),
            Seed("VM-IDFCFB-S", "Your IDFC FIRST Bank A/C XX9087 has been credited with INR 1,200.00 on 08-OCT-2026 by UPI from neha.s@acme. Avl Bal INR 6,511.25", ageMinutes = 75),
            Seed("JM-INDUSB-T", "Dear Customer, your IndusInd Bank Credit Card XX4410 has been used for INR 3,240.00 at ACME FUEL on 08-10-2026. Avl limit INR 1,42,760.00", ageMinutes = 140, read = true),
            Seed("VM-PNBSMS-S", "Your a/c XX7731 is debited for Rs.4,500.00 on 07-10-2026 and credited to a/c XX0099 (IMPS Ref no 628011420931). -PNB", ageMinutes = 2100, read = true),
            Seed("AD-UNIONB-S", "A/c *8890 Debited for Rs:1,999.00 on 05-10-2026 by Mob Bk ref no 527841920113 Avl Bal Rs:22,180.07. Never share OTP/PIN. -Union Bank of India", ageMinutes = 5600, read = true),
            Seed("VK-YESBNK-T", "582913 is the OTP for INR 2,499.00 txn at CITY MART on YES BANK card ending 4521. Valid for 10 mins. Do not share it with anyone.", ageMinutes = 8),
            Seed("VM-PAYTMB-S", "Rs.150 sent to ACME CAFE from Paytm Payments Bank A/c XX2210. UPI Ref: 628104990012. Not you? Report at paytm.com/care", ageMinutes = 260, read = true),
            Seed("AX-PHONPE-S", "New login to your PhonePe account on a Pixel device at 08-Oct 09:41 PM. If this wasn't you, block your account from the app immediately.", ageMinutes = 48),
            Seed("VM-LICIND-S", "Dear Policyholder, premium of Rs.12,034.00 for policy XXXX5521 is due on 20-10-2026. Pay online at licindia.in to keep your policy in force. -LIC", ageMinutes = 3000, read = true),
            Seed("JD-JIOINF-S", "Your Jio plan expires in 2 days.\nPlan: Rs.349 | 2GB/day | Unlimited calls\nRecharge now to continue enjoying uninterrupted services: jio.example/r", ageMinutes = 650),
            Seed("VM-VICARE-S", "Dear Customer, 90% of your daily data quota is used. Your data speed will reduce to 64 kbps after 100%. Add a data pack on the Vi app. -Vi", ageMinutes = 1500, read = true),
            Seed("VM-Airtel-S", "Your Airtel Thanks bill of Rs.599 for Sep-26 is generated. Due date: 18-Oct-26. Pay via the Airtel Thanks app.", ageMinutes = 2200, read = true),
            Seed("JK-blnkit-S", "Your Blinkit order of 6 items is on its way! Track live: blinkit.example/t/AB12cd", ageMinutes = 20),
            Seed("VM-ZEPTON-S", "8213 is the delivery code for your Zepto order 554120. Share it only with the delivery partner at your doorstep.", ageMinutes = 16),
            Seed("JM-SWIGGY-S", "Your Swiggy order from ACME BIRYANI is out for delivery and will reach in 12 mins. Never share OTP with anyone calling about this order.", ageMinutes = 27, read = true),
            Seed("VK-BIGBKT-S", "Your bigbasket order BB-889123 has been delivered. Rate your experience: bigbasket.example/r", ageMinutes = 3900, read = true),
            Seed("BP-NYKAAA-P", "\u2728 Glow up time! Flat 30% off on skincare + free gift on orders above Rs.999. Shop now: nykaa.example/glow T&C", ageMinutes = 7000, read = true),
            Seed("VM-OLACAB-S", "Ride booked: White Sedan KA01AB1234, Driver: Ravi (98xxxxxx10), Pickup: 09 Oct 2026 10:30 AM. Share OTP: 4821 with the driver to start the ride.", ageMinutes = 10),
            Seed("VM-IRCTCi-S", "PNR 4729381056: Train 12951 Coach B2 Berth 34 confirmed for 14-Oct-2026. Charting at 20:15. Never share OTP with anyone claiming to be from Railways.", ageMinutes = 1300, read = true),

            // Shapes that must NOT get a brand logo
            Seed("AD-Indigo-P", "Diwali colours are here! Get 15% off on premium wall paints this week. Visit your nearest dealer. -Indigo Paints", ageMinutes = 9000, read = true),
            Seed("AX-QWZXKP-S", "Your appointment with Dr. A. Rao is confirmed for 10-Oct-2026 at 11:00 AM. Reply C to cancel. -Acme Clinic", ageMinutes = 820),
            Seed("VM-650018-P", "Mega weekend sale! Up to 60% off on electronics. Limited stock. Shop now: acmeshop.example/sale", ageMinutes = 6200, read = true),
            Seed("57575", "Your request has been received. Reference 662810. We never ask for OTP.", ageMinutes = 4700, read = true),

            // Government (-G): long, multi-line, Devanagari
            Seed("JZ-NDMAEW-G", "Weather alert: Heavy to very heavy rainfall is likely in your district during the next 48 hours. Avoid low-lying areas and stay indoors during thunderstorms.\n\u092d\u093e\u0930\u0940 \u092c\u093e\u0930\u093f\u0936 \u0915\u0940 \u0938\u0902\u092d\u093e\u0935\u0928\u093e \u0939\u0948, \u0938\u0941\u0930\u0915\u094d\u0937\u093f\u0924 \u0930\u0939\u0947\u0902\u0964", ageMinutes = 90),

            // Investment / demat, long multipart
            Seed("VM-NSESMS-S", "Trade confirmation: You bought 10 shares of ACME LTD at Rs.1,842.50 on NSE on 08-Oct-2026. Trade value Rs.18,425.00. Your broker will send the contract note within 24 hours. If you did not place this order, report to your broker and the exchange immediately.", ageMinutes = 1200, read = true),
            Seed("VM-CDSLTX-S", "736104 is the OTP to link your demat account with the depository. Do not share your OTP with anyone. -CDSL", ageMinutes = 45),
        )
    }
}
