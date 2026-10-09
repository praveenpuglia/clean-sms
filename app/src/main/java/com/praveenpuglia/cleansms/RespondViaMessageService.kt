package com.praveenpuglia.cleansms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

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
        val number = intent.data?.schemeSpecificPart
            ?: intent.getStringExtra("address")
            ?: intent.getStringExtra("phone")
            ?: return
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (body.isBlank()) return
        val subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            .takeIf(SubscriptionManager::isValidSubscriptionId)

        try {
            SmsSender.send(this, number, body, subscriptionId)
            Log.d(TAG, "Quick reply sent")
        } catch (e: RuntimeException) {
            // Component boundary: a failed quick reply must not crash the process.
            Log.e(TAG, "Failed quick reply: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "RespondViaMessage"
    }
}
