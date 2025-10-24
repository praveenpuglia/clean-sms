package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

class OtpCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val otp = intent.getStringExtra(EXTRA_OTP) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("OTP", otp))
        Toast.makeText(context, context.getString(R.string.toast_otp_copied), Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_OTP = "extra_otp"
        const val ACTION_COPY_OTP = "com.praveenpuglia.cleansms.ACTION_COPY_OTP"
    }
}
