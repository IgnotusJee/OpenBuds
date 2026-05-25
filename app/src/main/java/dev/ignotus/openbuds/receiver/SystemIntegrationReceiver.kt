package dev.ignotus.openbuds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.ignotus.openbuds.QuickPopupActivity
import dev.ignotus.openbuds.lsposed.CrossProcessActions

class SystemIntegrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received: ${intent.action}")

        when (intent.action) {
            CrossProcessActions.ACTION_SHOW_QUICK_POPUP -> {
                val popupIntent = Intent(context, QuickPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.getStringExtra(CrossProcessActions.EXTRA_DEVICE_MAC)
                        ?.let { putExtra(CrossProcessActions.EXTRA_DEVICE_MAC, it) }
                }
                context.startActivity(popupIntent)
            }
            CrossProcessActions.ACTION_QUERY_DEVICE_MAC -> {
                // Phase 5+: respond with MAC if currently connected Sony device matches
                Log.d(TAG, "QUERY_DEVICE_MAC received — stub, full implementation in Phase 5")
            }
        }
    }

    companion object {
        private const val TAG = "OpenBuds"
    }
}
