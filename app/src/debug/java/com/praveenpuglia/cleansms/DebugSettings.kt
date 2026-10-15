package com.praveenpuglia.cleansms

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only settings helper. This entire file is excluded from release builds.
 */
object DebugSettings {

    // Random OTP message templates with placeholders
    private val otpTemplates = listOf(
        // Standard OTP formats
        Triple("HDFCBANK", "Your OTP for transaction is {OTP}. Valid for 5 minutes. Do not share with anyone.", "standard"),
        Triple("SBIBANK", "OTP: {OTP} for your SBI account login. Expires in 10 mins. Never share this code.", "standard"),
        Triple("ICICI", "{OTP} is your one-time password for ICICI Bank. Valid for 3 minutes.", "standard"),
        Triple("AMAZON", "Your Amazon verification code is {OTP}. Don't share this code with anyone.", "standard"),
        Triple("GOOGLE", "{OTP} is your Google verification code.", "standard"),
        Triple("PAYTM", "Your Paytm login code is {OTP}. Valid for 5 mins. Do NOT share.", "standard"),

        // Code-first formats (like Medplus)
        Triple("MEDPLUS", "{OTP} 2 hrs is the authorization code for your Medplus Login. Please use it within <#>. Thanks, Customer Care team", "code-first"),
        Triple("APOLLO", "{OTP} is the access code for Apollo Pharmacy. Use within 10 minutes.", "code-first"),
        Triple("IRCTC", "{OTP} is your authentication code for IRCTC booking. Valid for 5 mins.", "code-first"),

        // Generic code patterns
        Triple("FLIPKART", "The code for your Flipkart login is {OTP}. Keep it safe.", "generic"),
        Triple("SWIGGY", "Your code: {OTP}. Use it to verify your Swiggy order.", "generic"),
        Triple("ZOMATO", "Use {OTP} as your Zomato verification code. Expires in 5 min.", "generic"),

        // Time-validity based
        Triple("PHONEPE", "Use {OTP} within 3 minutes for PhonePe transaction. Never share this.", "time-validity"),
        Triple("GPAY", "{OTP} - Google Pay code. Expires in 5 minutes.", "time-validity"),

        // Authorization/Auth code formats
        Triple("BOOKMYSHOW", "{OTP} is your authorization code for BookMyShow. Valid for 10 mins.", "auth"),
        Triple("MAKEMYTRIP", "Your auth code is {OTP}. Use it to complete your MakeMyTrip booking.", "auth"),

        // Edge cases
        Triple("TESTBANK", "Dear Customer, {OTP} is the OTP for Rs. 5000 transaction. Do not share.", "with-amount"),
        Triple("UNKNOWN", "Code {OTP} for login. Thanks.", "minimal"),
    )

    private fun generateRandomOtp(length: Int = 6): String {
        return (1..length).map { (0..9).random() }.joinToString("")
    }

    // Ordinary messages (no OTP) across categories, to try notifications that aren't OTPs
    private val messageTemplates = listOf(
        Pair("+919876500011", "Running 10 mins late, start without me"),
        Pair("+919876500012", "Did you see the photos I sent? Call when free."),
        Pair("VM-HDFCBK-T", "Rs.1,250.00 credited to A/c XX1234 by UPI from ANANYA R. Avl Bal: Rs.27,840.50"),
        Pair("JM-JIOINF-S", "Your Jio plan expires in 2 days. Recharge now to continue enjoying uninterrupted services."),
        Pair("BP-NYKAAA-P", "Flat 30% off on skincare this weekend. Shop now: nykaa.example/glow"),
    )

    private fun getRandomOtpMessage(): Triple<String, String, String> {
        val template = otpTemplates.random()
        val otpLength = listOf(4, 5, 6, 6, 6, 8).random() // Weighted towards 6 digits
        val otp = generateRandomOtp(otpLength)
        val body = template.second.replace("{OTP}", otp)
        return Triple(template.first, body, template.third)
    }

    @Composable
    fun Content() {
        val context = LocalContext.current
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.settings_debug),
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
        )
        Spacer(Modifier.height(12.dp))
        val scope = rememberCoroutineScope()
        // Through the real delivery path: stored like an incoming SMS, then notified exactly as one
        val deliver = { sender: String, body: String ->
            scope.launch(Dispatchers.IO) { SmsDeliverReceiver.deliver(context.applicationContext, sender, body) }
        }
        OutlinedButton(
            onClick = {
                val (sender, body, type) = getRandomOtpMessage()
                deliver(sender, body)
                showTypeToast(context, type)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_test_random_otp))
        }
        OutlinedButton(
            onClick = {
                val (sender, body) = messageTemplates.random()
                deliver(sender, body)
                // Non-OTP SMS use the default-importance channel: status bar only, no pop-up
                Toast.makeText(context, context.getString(R.string.settings_test_random_message_sent, sender), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_test_random_message))
        }
    }

    private fun showTypeToast(context: Context, type: String) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_test_random_otp_type, type),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
