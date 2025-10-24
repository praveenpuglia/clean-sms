package com.praveenpuglia.cleansms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.telephony.SmsManager
import android.util.Log
import android.net.Uri

/**
 * Service to handle quick reply (respond-via-message) actions from the Phone app.
 * Requires android.permission.SEND_RESPOND_VIA_MESSAGE and intent-filter for ACTION_RESPOND_VIA_MESSAGE.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            handleRespondViaMessage(intent)
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun handleRespondViaMessage(intent: Intent) {
        try {
            val uri: Uri? = intent.data
            val number = uri?.schemeSpecificPart
                ?: intent.getStringExtra("address")
                ?: intent.getStringExtra("phone")
                ?: return

            val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra("android.intent.extra.TEXT")
                ?: ""
            if (body.isBlank()) return

            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, body, null, null)
            Log.d("RespondViaMessage", "Quick reply sent to $number")
        } catch (e: Exception) {
            Log.e("RespondViaMessage", "Failed quick reply", e)
        }
    }
}
