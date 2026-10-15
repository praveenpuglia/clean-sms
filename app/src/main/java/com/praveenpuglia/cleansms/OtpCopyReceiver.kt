package com.praveenpuglia.cleansms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Copy on an OTP notification. Copying uses the code, so it also marks the thread read and dismisses it. */
class OtpCopyReceiver : MarkReadReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val otp = intent.getStringExtra(EXTRA_OTP) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("OTP", otp))
        Toast.makeText(context, context.getString(R.string.toast_otp_copied), Toast.LENGTH_SHORT).show()
        super.onReceive(context, intent)
    }

    companion object {
        const val EXTRA_OTP = "extra_otp"
        const val ACTION_COPY_OTP = "com.praveenpuglia.cleansms.ACTION_COPY_OTP"

        fun intent(context: Context, otp: String, notificationId: Int, threadId: Long): Intent =
            Intent(context, OtpCopyReceiver::class.java)
                .setAction(ACTION_COPY_OTP)
                .putExtra(EXTRA_OTP, otp)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_THREAD_ID, threadId)
    }
}
