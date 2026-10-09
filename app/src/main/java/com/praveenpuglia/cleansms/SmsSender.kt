package com.praveenpuglia.cleansms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

/** Single send path for thread replies, new messages and quick replies. */
object SmsSender {
    private const val TAG = "SmsSender"

    /**
     * Records [body] in the Outbox, sends it (split into parts when needed) and lets
     * [SmsSentReceiver] move it to Sent or Failed once the radio reports back. The default SMS app
     * must do this bookkeeping itself. Throws [SecurityException]/[IllegalArgumentException] when
     * the send can't even be queued; the row is marked Failed first. Blocking: call off the main
     * thread when possible.
     */
    fun send(context: Context, address: String, body: String, subscriptionId: Int? = null, threadId: Long? = null) {
        val row = recordOutgoing(context, address, body, subscriptionId, threadId)
        val default = context.getSystemService(SmsManager::class.java)
        val manager = subscriptionId?.let(default::createForSubscriptionId) ?: default
        val parts = manager.divideMessage(body)
        val sentIntent = row?.let { sentIntent(context, it) }
        try {
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(address, null, parts, sentIntent?.let { pi -> ArrayList(parts.map { pi }) }, null)
            } else {
                manager.sendTextMessage(address, null, body, sentIntent, null)
            }
        } catch (e: RuntimeException) {
            row?.let { SmsSentReceiver.markFailed(context, it) }
            throw e
        }
    }

    private fun sentIntent(context: Context, row: Uri): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        // The row URI as data keeps each message's PendingIntent distinct.
        Intent(SmsSentReceiver.ACTION_SMS_SENT, row, context, SmsSentReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Inserts the Outbox row; null when the provider refuses (sending still proceeds untracked). */
    private fun recordOutgoing(context: Context, address: String, body: String, subscriptionId: Int?, threadId: Long?): Uri? {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.THREAD_ID, threadId ?: Telephony.Threads.getOrCreateThreadId(context, setOf(address)))
                subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: SecurityException) {
            Log.w(TAG, "Outgoing message not recorded: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Outgoing message not recorded: ${e.javaClass.simpleName}")
            null
        } catch (e: SQLiteException) {
            Log.w(TAG, "Outgoing message not recorded: ${e.javaClass.simpleName}")
            null
        }
    }
}
