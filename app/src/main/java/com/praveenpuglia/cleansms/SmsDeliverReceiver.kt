package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.content.ContentValues
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.graphics.BitmapFactory
import android.net.Uri
import android.app.PendingIntent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import kotlin.math.absoluteValue
import com.praveenpuglia.cleansms.R


class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        Log.d("SmsDeliver", "SMS_DELIVER received")

        if (!DefaultSmsHelper.isDefaultSmsApp(context)) {
            Log.d("SmsDeliver", "Not default SMS app; ignoring deliver action")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
        val originatingAddress = messages.first().originatingAddress ?: return

        // Insert into SMS provider (Inbox)
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, originatingAddress)
                put(Telephony.Sms.BODY, fullBody)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
            }
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            Log.d("SmsDeliver", "Inserted SMS into provider for $originatingAddress")
        } catch (e: Exception) {
            Log.e("SmsDeliver", "Failed inserting SMS", e)
        }

        // Enrich for notification
        val enriched = ContactEnrichment.enrich(context, originatingAddress)
        val displayName = enriched?.name ?: originatingAddress
        val photoUri = enriched?.photoUri
        val lookupUri = enriched?.lookupUri

    val threadId = findOrCreateThreadId(context, originatingAddress)
    val category = CategoryStorage.getCategoryOrCompute(context, originatingAddress, threadId)
    // Unified high precision OTP detection (keyword + proximity) per CategoryClassifier
    val otpCode = CategoryClassifier.extractHighPrecisionOtp(fullBody)

        postNotification(
            context = context,
            threadId = threadId,
            address = originatingAddress,
            title = displayName,
            body = fullBody,
            photoUri = photoUri,
            lookupUri = lookupUri,
            category = category,
            otpCode = otpCode
        )

        // Refresh threads list UI
        MainActivity.refreshThreadsIfActive()

        // Prevent other apps from also handling (ordered broadcast); default app can abort
        abortBroadcast()
    }

    private fun postNotification(
        context: Context,
        threadId: Long,
        address: String,
        title: String,
        body: String,
        photoUri: String?,
        lookupUri: String?,
        category: MessageCategory,
        otpCode: String?
    ) {
        val channelId = "incoming_sms"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(channelId, "Incoming SMS", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "Notifications for received SMS messages"
                nm.createNotificationChannel(ch)
            }
        }

        val contentIntent = createThreadDetailPendingIntent(
            context = context,
            threadId = threadId,
            address = address,
            displayName = title,
            photoUri = photoUri,
            category = category,
            lookupUri = lookupUri
        )

        val notificationId = address.hashCode()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body.take(120))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (!otpCode.isNullOrEmpty()) {
            // Don't override content text - keep the SMS body visible
            
            val copyIntent = Intent(context, OtpCopyReceiver::class.java).apply {
                action = OtpCopyReceiver.ACTION_COPY_OTP
                putExtra(OtpCopyReceiver.EXTRA_OTP, otpCode)
                putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                (otpCode.hashCode() xor address.hashCode()).absoluteValue,
                copyIntent,
                flags
            )

            val remoteViews = RemoteViews(context.packageName, R.layout.notification_otp).apply {
                setTextViewText(R.id.notification_otp_text, otpCode)
                setTextViewText(R.id.notification_sender_text, title)
                setOnClickPendingIntent(R.id.notification_copy_button, copyPendingIntent)
                setImageViewResource(R.id.notification_copy_button, R.drawable.ic_copy)
            }

            builder.setCustomContentView(remoteViews)
            builder.setCustomBigContentView(remoteViews)
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }
        val bmp = loadBitmap(context, photoUri)
        if (bmp != null) builder.setLargeIcon(bmp)
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun createThreadDetailPendingIntent(
        context: Context,
        threadId: Long,
        address: String,
        displayName: String?,
        photoUri: String?,
        category: MessageCategory,
        lookupUri: String?
    ): PendingIntent {
        val detailIntent = Intent(context, ThreadDetailActivity::class.java).apply {
            putExtra("THREAD_ID", threadId)
            putExtra("CONTACT_NAME", displayName)
            putExtra("CONTACT_ADDRESS", address)
            putExtra("CONTACT_PHOTO_URI", photoUri)
            putExtra("CATEGORY", category.name)
            putExtra("CONTACT_LOOKUP_URI", lookupUri)
        }

        val stackBuilder = TaskStackBuilder.create(context).apply {
            addNextIntent(Intent(context, MainActivity::class.java))
            addNextIntent(detailIntent)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        val requestCode = (threadId xor address.hashCode().toLong()).toInt()
        return stackBuilder.getPendingIntent(requestCode, flags)
            ?: PendingIntent.getActivity(context, requestCode, detailIntent, flags)
    }

    private fun findOrCreateThreadId(context: Context, address: String): Long {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("thread_id"),
            "address = ?",
            arrayOf(address),
            "date DESC LIMIT 1"
        )?.use { cursor ->
            val idx = cursor.getColumnIndex("thread_id")
            if (idx >= 0 && cursor.moveToFirst()) {
                val threadId = cursor.getLong(idx)
                if (threadId > 0) return threadId
            }
        }

        return Telephony.Threads.getOrCreateThreadId(context, setOf(address))
    }

    // Legacy simple OTP extraction removed; use CategoryClassifier.extractHighPrecisionOtp at call sites.

    private fun loadBitmap(context: Context, uriString: String?): android.graphics.Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
        } catch (_: Exception) { null }
    }
}
