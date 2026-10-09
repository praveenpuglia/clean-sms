package com.praveenpuglia.cleansms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.IOException
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        Log.d(TAG, "SMS_DELIVER received")

        if (!DefaultSmsHelper.isDefaultSmsApp(context)) {
            Log.d(TAG, "Not default SMS app; ignoring deliver action")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody ?: it.messageBody ?: "" }
        val originatingAddress = messages.first().originatingAddress ?: return

        // Provider writes, contact lookups and bitmap decoding must not run on the main thread.
        // goAsync keeps the process alive until finish() (system limit ~10s).
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                deliver(appContext, originatingAddress, fullBody)
            } finally {
                // SMS_DELIVER is ordered; the default app consumes it.
                pending.abortBroadcast()
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsDeliver"
        // Top-level boundary for background delivery: log instead of crashing the process.
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e(TAG, "Delivery failed: ${e.javaClass.simpleName}")
            },
        )
        private val mainHandler = Handler(Looper.getMainLooper())

        /** Stores the message and notifies. Blocking: call off the main thread. */
        internal fun deliver(context: Context, address: String, body: String) {
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed inserting SMS: ${e.javaClass.simpleName}")
            } catch (e: SQLiteException) {
                Log.e(TAG, "Failed inserting SMS: ${e.javaClass.simpleName}")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed inserting SMS: ${e.javaClass.simpleName}")
            }

            val enriched = ContactEnrichment.enrich(context, address)
            val threadId = findOrCreateThreadId(context, address)
            postNotification(
                context = context,
                threadId = threadId,
                address = address,
                title = enriched?.name ?: address,
                body = body,
                photoUri = enriched?.photoUri,
                lookupUri = enriched?.lookupUri,
                category = CategoryStorage.getCategoryOrCompute(context, address, threadId),
                otpCode = CategoryClassifier.extractHighPrecisionOtp(body),
            )

            mainHandler.post { MainActivity.refreshThreadsIfActive() }
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
            otpCode: String?,
        ) {
            val isOtp = !otpCode.isNullOrEmpty()

            // Honor the "Promotional notifications" preference. The SMS is already in
            // the provider — this only suppresses the system notification. OTP detections
            // still fire regardless of category so users never miss a code.
            if (!isOtp && category == MessageCategory.PROMOTIONAL &&
                !SettingsActivity.getPromoNotificationsEnabled(context)
            ) {
                return
            }

            ensureChannels(context)
            val channelId = if (isOtp) CHANNEL_OTP else CHANNEL_SMS
            val notificationId = address.hashCode()

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(createThreadDetailPendingIntent(context, threadId, address, title, photoUri, category, lookupUri))

            if (isOtp) {
                // Set high priority for OTP notifications to show as heads-up
                builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)

                val copyIntent = Intent(context, OtpCopyReceiver::class.java).apply {
                    action = OtpCopyReceiver.ACTION_COPY_OTP
                    putExtra(OtpCopyReceiver.EXTRA_OTP, otpCode)
                    putExtra(OtpCopyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val copyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (otpCode.hashCode() xor address.hashCode()).absoluteValue,
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val remoteViews = RemoteViews(context.packageName, R.layout.notification_otp).apply {
                    setTextViewText(R.id.notification_otp_text, otpCode)
                    setTextViewText(R.id.notification_sender_text, title)
                    setOnClickPendingIntent(R.id.notification_copy_button, copyPendingIntent)
                    setImageViewResource(R.id.notification_copy_button, R.drawable.ic_copy)
                }
                builder.setCustomContentView(remoteViews)
                    .setCustomBigContentView(remoteViews)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            }
            loadBitmap(context, photoUri)?.let(builder::setLargeIcon)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }

        private fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_OTP) == null) {
                // HIGH importance for heads-up display
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_OTP, "OTP Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "High-priority notifications for OTP codes"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                )
            }
            if (nm.getNotificationChannel(CHANNEL_SMS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_SMS, "Incoming SMS", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Notifications for received SMS messages"
                    },
                )
            }
        }

        private fun createThreadDetailPendingIntent(
            context: Context,
            threadId: Long,
            address: String,
            displayName: String?,
            photoUri: String?,
            category: MessageCategory,
            lookupUri: String?,
        ): PendingIntent {
            val detailIntent = Intent(context, ThreadDetailActivity::class.java).apply {
                putExtra("THREAD_ID", threadId)
                putExtra("CONTACT_NAME", displayName)
                putExtra("CONTACT_ADDRESS", address)
                putExtra("CONTACT_PHOTO_URI", photoUri)
                putExtra("CATEGORY", category.name)
                putExtra("CONTACT_LOOKUP_URI", lookupUri)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val requestCode = (threadId xor address.hashCode().toLong()).toInt()
            return TaskStackBuilder.create(context)
                .addNextIntent(Intent(context, MainActivity::class.java))
                .addNextIntent(detailIntent)
                .getPendingIntent(requestCode, flags)
                ?: PendingIntent.getActivity(context, requestCode, detailIntent, flags)
        }

        private fun findOrCreateThreadId(context: Context, address: String): Long {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("thread_id"),
                "address = ?",
                arrayOf(address),
                "date DESC LIMIT 1",
            )?.use { cursor ->
                val idx = cursor.getColumnIndex("thread_id")
                if (idx >= 0 && cursor.moveToFirst()) {
                    val threadId = cursor.getLong(idx)
                    if (threadId > 0) return threadId
                }
            }
            return Telephony.Threads.getOrCreateThreadId(context, setOf(address))
        }

        private fun loadBitmap(context: Context, uriString: String?): Bitmap? {
            if (uriString.isNullOrBlank()) return null
            return try {
                context.contentResolver.openInputStream(uriString.toUri())?.use(BitmapFactory::decodeStream)
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

        internal const val CHANNEL_OTP = "otp_sms"
        internal const val CHANNEL_SMS = "incoming_sms"
    }
}
