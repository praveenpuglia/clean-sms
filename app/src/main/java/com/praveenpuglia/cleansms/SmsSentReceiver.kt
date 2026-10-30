package com.praveenpuglia.cleansms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

/**
 * Radio result for a message queued by [SmsSender]: moves its row from Outbox to Sent, or to
 * Failed. Multipart messages report once per part; any failed part fails the message. Also
 * records the carrier's delivery report in the row's status.
 */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.data ?: return
        // ponytail: single-row update on the main thread, small enough to skip goAsync.
        when (intent.action) {
            ACTION_SMS_SENT -> if (resultCode == Activity.RESULT_OK) markSent(context, row) else markFailed(context, row)
            ACTION_SMS_DELIVERED -> deliveryStatus(intent)?.let { update(context, row, ContentValues().apply { put(Telephony.Sms.STATUS, it) }, null) }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.praveenpuglia.cleansms.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.praveenpuglia.cleansms.ACTION_SMS_DELIVERED"
        private const val TAG = "SmsSentReceiver"

        fun markFailed(context: Context, row: Uri) = setType(context, row, Telephony.Sms.MESSAGE_TYPE_FAILED, null)

        // A later part's OK must not overwrite an earlier part's failure.
        private fun markSent(context: Context, row: Uri) = setType(
            context,
            row,
            Telephony.Sms.MESSAGE_TYPE_SENT,
            "${Telephony.Sms.TYPE} != ${Telephony.Sms.MESSAGE_TYPE_FAILED}",
        )

        /**
         * The status report's outcome as a [Telephony.Sms] STATUS_ value, or null when the report
         * can't be read. 3GPP TP-Status: below 0x20 delivered, below 0x40 the carrier is still
         * trying, anything else failed. 3GPP2 carries an error class in the top byte instead.
         */
        fun deliveryStatus(intent: Intent): Int? {
            val pdu = intent.getByteArrayExtra("pdu") ?: return null
            val format = intent.getStringExtra("format")
            val raw = SmsMessage.createFromPdu(pdu, format)?.status ?: return null
            val status = if (format == SmsMessage.FORMAT_3GPP2) {
                when (raw ushr 24) { 0 -> 0x00; 2 -> 0x20; else -> 0x40 }
            } else raw
            return when {
                status < 0x20 -> Telephony.Sms.STATUS_COMPLETE
                status < 0x40 -> Telephony.Sms.STATUS_PENDING
                else -> Telephony.Sms.STATUS_FAILED
            }
        }

        private fun setType(context: Context, row: Uri, type: Int, where: String?) =
            update(context, row, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, where)

        private fun update(context: Context, row: Uri, values: ContentValues, where: String?) {
            try {
                context.contentResolver.update(row, values, where, null)
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
