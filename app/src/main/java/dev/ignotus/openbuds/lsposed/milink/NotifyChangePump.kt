package dev.ignotus.openbuds.lsposed.milink

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log

class NotifyChangePump(
    private val context: Context,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var lastNotifyMs: Long = 0L

    fun requestNotify() {
        val current = now()
        if (current - lastNotifyMs < MIN_INTERVAL_MS) return
        lastNotifyMs = current
        runCatching {
            context.contentResolver.notifyChange(AIRPODS_STATE_URI, null)
        }.onFailure { error ->
            Log.w(TAG, "notifyChange failed; waiting for passive MiLink polling", error)
        }
    }

    private companion object {
        private const val TAG = "OpenBuds"
        private const val MIN_INTERVAL_MS = 1_000L
        private val AIRPODS_STATE_URI: Uri =
            Uri.parse("content://com.android.bluetooth.ble.app.headsetdata.provider/airpodsstate")
    }
}
