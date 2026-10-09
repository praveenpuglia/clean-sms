package com.praveenpuglia.cleansms

import android.app.role.RoleManager
import android.content.Context
import android.provider.Telephony

object DefaultSmsHelper {
    fun isDefaultSmsApp(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
            return true
        }
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}
