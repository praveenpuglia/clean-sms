package com.praveenpuglia.cleansms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton
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

    fun setupDebugSection(activity: SettingsActivity, container: ViewGroup) {
        // Add debug header
        val header = TextView(activity).apply {
            text = "Debug"
            setTextColor(activity.getColor(com.google.android.material.R.color.design_default_color_error))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.05f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (32 * activity.resources.displayMetrics.density).toInt()
                bottomMargin = (12 * activity.resources.displayMetrics.density).toInt()
            }
            layoutParams = params
        }
        container.addView(header)

        // Add random OTP test button
        val testButton = MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Test Random OTP"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                val (sender, body, type) = getRandomOtpMessage()
                postTestOtpNotification(context = activity, sender = sender, body = body)
                android.widget.Toast.makeText(activity, "Type: $type", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(testButton)
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
