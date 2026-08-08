package com.praveenpuglia.cleansms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.absoluteValue

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
        OutlinedButton(
            onClick = {
                val (sender, body, type) = getRandomOtpMessage()
                postTestOtpNotification(context, sender, body)
                showTypeToast(context, type)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_test_random_otp))
        }
    }

    private fun showTypeToast(context: Context, type: String) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_test_random_otp_type, type),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun postTestOtpNotification(context: Context, sender: String, body: String) {
        val channelId = "otp_sms"
        // Extract OTP using the actual classifier to test the detection logic
        val testOtp = CategoryClassifier.extractHighPrecisionOtp(body) ?: "NO_OTP_FOUND"
        val testSender = sender
        val testBody = body

        // Ensure channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                val otpChannel = NotificationChannel(
                    channelId,
                    "OTP Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority notifications for OTP codes"
                    enableVibration(true)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(otpChannel)
            }
        }

        val notificationId = testSender.hashCode()

        // Create copy intent
        val copyIntent = Intent(context, OtpCopyReceiver::class.java).apply {
            action = OtpCopyReceiver.ACTION_COPY_OTP
            putExtra(OtpCopyReceiver.EXTRA_OTP, testOtp)
            putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            (testOtp.hashCode() xor testSender.hashCode()).absoluteValue,
            copyIntent,
            flags
        )

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_otp).apply {
            setTextViewText(R.id.notification_otp_text, testOtp)
            setTextViewText(R.id.notification_sender_text, testSender)
            setOnClickPendingIntent(R.id.notification_copy_button, copyPendingIntent)
            setImageViewResource(R.id.notification_copy_button, R.drawable.ic_copy)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(testSender)
            .setContentText(testBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
