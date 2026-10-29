package com.praveenpuglia.cleansms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Opt-in automatic deletion of received OTPs ([AppSettings.OtpAutoDelete]). There is no polling: one
 * inexact, non-waking alarm is set for when the oldest kept OTP falls due, and each run only checks
 * messages that became due since the last one. Starred OTPs are never deleted.
 *
 * Runs on that alarm, after a reboot (alarms don't survive one) and after an app update.
 */
class OtpCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                run(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "OtpCleanup"
        private const val ACTION_CLEANUP = "com.praveenpuglia.cleansms.ACTION_OTP_CLEANUP"
        private const val DELETE_BATCH = 500
        private const val ALARM_WINDOW = 15 * 60_000L
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e(TAG, "OTP cleanup failed: ${e.javaClass.simpleName}")
            },
        )

        /** Runs [run] off the main thread, e.g. right after the setting changes. */
        fun request(context: Context) {
            context.sendBroadcast(Intent(context, OtpCleanupReceiver::class.java))
        }

        /** Deletes the OTPs that are due and sets the alarm for the next one. Blocking: call off the main thread. */
        fun run(context: Context, now: Long = System.currentTimeMillis()) {
            val keepFor = AppSettings.getOtpAutoDelete(context).after.inWholeMilliseconds
            val alarms = context.getSystemService(AlarmManager::class.java)
            if (keepFor == 0L) {
                alarmIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
                return
            }
            val cutoff = now - keepFor
            try {
                deleteDue(context, cutoff)
                val next = oldestKeptOtpDate(context, cutoff)
                if (next == null) {
                    alarmIntent(context, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
                } else {
                    // Non-waking, with a bounded window the system can batch into: deleting a few minutes
                    // late is fine, waking the phone for it isn't. Plain set() may run up to 75% of the delay late.
                    val intent = alarmIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
                    alarms.setWindow(AlarmManager.RTC, next + keepFor, ALARM_WINDOW, intent)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Can't clean up OTPs without the SMS role: ${e.javaClass.simpleName}")
            } catch (e: SQLiteException) {
                Log.w(TAG, "OTP cleanup failed: ${e.javaClass.simpleName}")
            }
        }

        /** The PendingIntent for the cleanup alarm, or null with FLAG_NO_CREATE when none is set. */
        internal fun alarmIntent(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, OtpCleanupReceiver::class.java).setAction(ACTION_CLEANUP),
            flag or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun deleteDue(context: Context, cutoff: Long) {
            val checkedUpTo = AppSettings.getOtpCleanupWatermark(context)
            if (cutoff <= checkedUpTo) return
            val starred = StarredMessages.ids(context)
            val due = mutableMapOf<Long, String?>()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.DATE} <= ?",
                arrayOf(checkedUpTo.toString(), cutoff.toString()),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val body = cursor.getString(2) ?: continue
                    if (id !in starred && CategoryClassifier.extractHighPrecisionOtp(body) != null) {
                        due[id] = cursor.getString(1)
                    }
                }
            }
            due.keys.chunked(DELETE_BATCH).forEach { ids ->
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} IN (${ids.joinToString(",") { "?" }})",
                    ids.map(Long::toString).toTypedArray(),
                )
            }
            // A notification still showing the code would outlive the message (one per sender)
            val notifications = NotificationManagerCompat.from(context)
            due.values.filterNotNull().toSet().forEach { notifications.cancel(it.hashCode()) }
            AppSettings.setOtpCleanupWatermark(context, cutoff)
            if (due.isNotEmpty()) Log.i(TAG, "Deleted ${due.size} OTPs")
        }

        /** Date of the oldest OTP that is still kept, or null when there is none. */
        private fun oldestKeptOtpDate(context: Context, cutoff: Long): Long? {
            val starred = StarredMessages.ids(context)
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val body = cursor.getString(1) ?: continue
                    if (cursor.getLong(0) !in starred && CategoryClassifier.extractHighPrecisionOtp(body) != null) {
                        return cursor.getLong(2)
                    }
                }
            }
            return null
        }
    }
}
