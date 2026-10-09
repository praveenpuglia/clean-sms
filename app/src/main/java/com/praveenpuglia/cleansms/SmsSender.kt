package com.praveenpuglia.cleansms

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

/** Single send path for thread replies, new messages and quick replies. */
object SmsSender {
    private const val TAG = "SmsSender"

    /**
     * Sends [body] (split into parts when needed) and records it in the Sent box, which the default
     * SMS app must do itself. Throws [SecurityException]/[IllegalArgumentException] when sending
     * fails; a failed provider write is only logged because the SMS has already gone out.
     * Blocking: call off the main thread when possible.
     */
    fun send(context: Context, address: String, body: String, subscriptionId: Int? = null, threadId: Long? = null) {
        val default = context.getSystemService(SmsManager::class.java)
        val manager = subscriptionId?.let(default::createForSubscriptionId) ?: default
        val parts = manager.divideMessage(body)
        if (parts.size > 1) {
            manager.sendMultipartTextMessage(address, null, parts, null, null)
        } else {
            manager.sendTextMessage(address, null, body, null, null)
        }
        recordSent(context, address, body, subscriptionId, threadId)
    }

    private fun recordSent(context: Context, address: String, body: String, subscriptionId: Int?, threadId: Long?) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, threadId ?: Telephony.Threads.getOrCreateThreadId(context, setOf(address)))
                subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sent message not recorded: ${e.javaClass.simpleName}")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Sent message not recorded: ${e.javaClass.simpleName}")
        } catch (e: SQLiteException) {
            Log.w(TAG, "Sent message not recorded: ${e.javaClass.simpleName}")
        }
    }
}
