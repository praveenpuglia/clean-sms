package com.praveenpuglia.cleansms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER") return
        Log.d("WapPush", "WAP_PUSH_DELIVER received (MMS placeholder)")
        // Placeholder: default SMS apps would parse MMS here. We keep minimal for role qualification.
    }
}
