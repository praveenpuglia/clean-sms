package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.content.ContentValues


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

        // Trigger UI refresh (threads list) and let IncomingSmsReceiver handle notification via SMS_RECEIVED broadcast
        MainActivity.refreshThreadsIfActive()
    }
}
