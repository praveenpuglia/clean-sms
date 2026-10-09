package com.praveenpuglia.cleansms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimSlots {
    /** Active SIMs, or empty when READ_PHONE_STATE is missing. */
    fun activeSims(context: Context): List<SubscriptionInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** Index of the default SMS SIM in [sims], or 0. */
    fun defaultIndex(sims: List<SubscriptionInfo>): Int {
        if (sims.size <= 1) return 0
        val defaultId = SubscriptionManager.getDefaultSmsSubscriptionId()
        return sims.indexOfFirst { it.subscriptionId == defaultId }.coerceAtLeast(0)
    }

    fun resolver(context: Context) = SimSlotResolver { id ->
        activeSims(context).firstOrNull { it.subscriptionId == id }?.simSlotIndex?.plus(1)
    }
}

/**
 * Maps subscription ids to 1-based SIM slots. Ids the system can't resolve (removed SIM, no
 * permission) get a stable 1..2 slot by first appearance so badges stay consistent in a list.
 */
class SimSlotResolver(private val lookupSlot: (Int) -> Int?) {
    private val cache = HashMap<Int, Int?>()
    private val fallbackOrder = mutableListOf<Int>()

    @Synchronized
    fun slotFor(subscriptionId: Int): Int? {
        if (cache.containsKey(subscriptionId)) return cache[subscriptionId]
        val slot = lookupSlot(subscriptionId) ?: run {
            if (subscriptionId !in fallbackOrder && fallbackOrder.size < 2) fallbackOrder += subscriptionId
            fallbackOrder.indexOf(subscriptionId).takeIf { it >= 0 }?.plus(1)
        }
        cache[subscriptionId] = slot
        return slot
    }
}
