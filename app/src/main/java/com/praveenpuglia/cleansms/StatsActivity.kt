package com.praveenpuglia.cleansms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.praveenpuglia.cleansms.ui.stats.StatsMessageRecord
import com.praveenpuglia.cleansms.ui.stats.StatsScreen
import com.praveenpuglia.cleansms.ui.theme.CleanSmsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsActivity : AppCompatActivity() {
    private var records by mutableStateOf<List<StatsMessageRecord>?>(null)
    private var loadFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        FontThemeHelper.apply(this)
        AppCompatDelegate.setDefaultNightMode(SettingsActivity.getThemeMode(this))
        super.onCreate(savedInstanceState)

        setContent {
            CleanSmsTheme {
                StatsScreen(
                    records = records,
                    loadFailed = loadFailed,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
        loadStats()
    }

    private fun loadStats() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            loadFailed = true
            return
        }
        lifecycleScope.launch {
            try {
                records = withContext(Dispatchers.IO) { queryStatsRecords() }
            } catch (error: SecurityException) {
                Log.w(TAG, "SMS permission was revoked while loading stats")
                loadFailed = true
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to load SMS stats", error)
                loadFailed = true
            }
        }
    }

    private fun queryStatsRecords(): List<StatsMessageRecord> {
        data class PendingRecord(
            val threadId: Long,
            val address: String,
            val date: Long,
            val type: Int,
            val isUnread: Boolean,
            val isOtp: Boolean,
            val isSpam: Boolean,
        )

        val pending = mutableListOf<PendingRecord>()
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
            ),
            "${Telephony.Sms.TYPE} IN (?, ?)",
            arrayOf(
                Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
            ),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val threadIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = cursor.getColumnIndex(Telephony.Sms.READ)
            if (listOf(threadIndex, addressIndex, bodyIndex, dateIndex, typeIndex, readIndex).any { it < 0 }) {
                return@use
            }

            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadIndex)
                val date = cursor.getLong(dateIndex)
                if (threadId < 0 || date <= 0) continue
                val type = cursor.getInt(typeIndex)
                val body = cursor.getString(bodyIndex).orEmpty()
                pending += PendingRecord(
                    threadId = threadId,
                    address = cursor.getString(addressIndex).orEmpty(),
                    date = date,
                    type = type,
                    isUnread = type == Telephony.Sms.MESSAGE_TYPE_INBOX && cursor.getInt(readIndex) == 0,
                    isOtp = type == Telephony.Sms.MESSAGE_TYPE_INBOX && CategoryClassifier.extractHighPrecisionOtp(body) != null,
                    isSpam = SpamDetector.isSpam(body),
                )
            }
        }

        val categories = pending.distinctBy { it.threadId }.associate { record ->
            record.threadId to CategoryStorage.getCategoryOrCompute(this, record.address, record.threadId)
        }
        return pending.map { record ->
            StatsMessageRecord(
                threadId = record.threadId,
                date = record.date,
                type = record.type,
                isUnread = record.isUnread,
                category = categories.getValue(record.threadId),
                isOtp = record.isOtp,
                isSpam = record.isSpam,
            )
        }
    }

    private companion object {
        const val TAG = "StatsActivity"
    }
}
