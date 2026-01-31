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

    fun setupDebugSection(activity: SettingsActivity, container: ViewGroup) {
        val inflater = LayoutInflater.from(activity)

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

        // Add test OTP button
        val testButton = MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Test OTP Notification"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = params
            setOnClickListener {
                postTestOtpNotification(activity)
            }
        }
        container.addView(testButton)
    }

    private fun postTestOtpNotification(context: Context) {
        val channelId = "otp_sms"
        val testOtp = "123456"
        val testSender = "TEST-BANK"
        val testBody = "Your OTP for transaction is $testOtp. Valid for 5 minutes. Do not share with anyone."

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
