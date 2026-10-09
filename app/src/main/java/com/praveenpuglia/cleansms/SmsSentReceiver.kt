package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Radio result for a message queued by [SmsSender]: moves its row from Outbox to Sent, or to
 * Failed. Multipart messages report once per part; any failed part fails the message.
 */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val row = intent.data ?: return
        // ponytail: single-row update on the main thread, small enough to skip goAsync.
        if (resultCode == Activity.RESULT_OK) markSent(context, row) else markFailed(context, row)
    }

    companion object {
        const val ACTION_SMS_SENT = "com.praveenpuglia.cleansms.ACTION_SMS_SENT"
        private const val TAG = "SmsSentReceiver"

        fun markFailed(context: Context, row: Uri) = setType(context, row, Telephony.Sms.MESSAGE_TYPE_FAILED, null)

        // A later part's OK must not overwrite an earlier part's failure.
        private fun markSent(context: Context, row: Uri) = setType(
            context,
            row,
            Telephony.Sms.MESSAGE_TYPE_SENT,
            "${Telephony.Sms.TYPE} != ${Telephony.Sms.MESSAGE_TYPE_FAILED}",
        )

        private fun setType(context: Context, row: Uri, type: Int, where: String?) {
            try {
                context.contentResolver.update(row, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, where, null)
            } catch (e: SecurityException) {
                Log.w(TAG, "Send result not recorded: ${e.javaClass.simpleName}")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Send result not recorded: ${e.javaClass.simpleName}")
            } catch (e: SQLiteException) {
                Log.w(TAG, "Send result not recorded: ${e.javaClass.simpleName}")
            }
        }
    }
}
