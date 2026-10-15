package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Mark as read" on an SMS notification: marks the sender's whole thread read and dismisses it. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId < 0) return
        val pending = goAsync() // provider write off the main thread
        val appContext = context.applicationContext
        scope.launch {
            try {
                markThreadRead(appContext, threadId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val TAG = "MarkRead"
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e(TAG, "Mark read failed: ${e.javaClass.simpleName}")
            },
        )

        /** Marks every unread message in [threadId] read. Blocking: call off the main thread. */
        fun markThreadRead(context: Context, threadId: Long) {
            try {
                context.contentResolver.update(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    ContentValues().apply { put(Telephony.Sms.READ, 1) },
                    "thread_id = ? AND read = 0",
                    arrayOf(threadId.toString()),
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Failed to mark thread read: ${error.javaClass.simpleName}")
            }
        }
    }
}
