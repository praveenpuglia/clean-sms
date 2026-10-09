package com.praveenpuglia.cleansms

import android.content.Context
import android.util.Log
import java.io.IOException

/**
 * Bundled brand logos for DLT sender headers. The map (assets/sender_brands.tsv) is generated from
 * data/brands.json: a header maps to a brand only when TRAI's registry lists that brand as its
 * owner, so look-alike headers never get a brand's logo. Lookups are exact and case-sensitive
 * (INDIGO is the airline, Indigo is a paint company). Everything stays on the device.
 */
object SenderBrands {
    private const val TAG = "SenderBrands"
    private val traiHeader = Regex("^[A-Za-z]{2}-([A-Za-z0-9]{1,6})(?:-[A-Za-z])?$")
    @Volatile private var brandByHeader: Map<String, String>? = null

    /**
     * Avatar image URI for [address] (e.g. "JD-HDFCBK-T"), or null when it isn't a known brand or
     * logos are turned off. Blocking on first use (reads the asset): call off the main thread.
     */
    fun logoUri(context: Context, address: String): String? {
        if (!AppSettings.getShowSenderLogos(context)) return null
        val brand = brandFor(context, address) ?: return null
        return "android.resource://${context.packageName}/drawable/brand_$brand"
    }

    fun brandFor(context: Context, address: String): String? {
        val header = headerOf(address) ?: return null
        return load(context)[header]
    }

    /** Middle part of a TRAI address, case preserved: "VM-IndiGo-S" -> "IndiGo". */
    fun headerOf(address: String): String? = traiHeader.matchEntire(address.trim())?.groupValues?.get(1)

    @Synchronized
    private fun load(context: Context): Map<String, String> {
        brandByHeader?.let { return it }
        val map = try {
            context.assets.open("sender_brands.tsv").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                    .toMap()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Brand map unavailable: ${e.javaClass.simpleName}")
            emptyMap()
        }
        brandByHeader = map
        return map
    }
}
