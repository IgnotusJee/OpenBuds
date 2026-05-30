package dev.ignotus.openbuds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.util.Log
import dev.ignotus.openbuds.MainActivity
import dev.ignotus.openbuds.lsposed.CrossProcessActions
import dev.ignotus.openbuds.service.SonyControlService

class SystemIntegrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callerPackages = context.packageManager
            .getPackagesForUid(Binder.getCallingUid())
            ?.toSet() ?: emptySet()

        val allowed = CrossProcessActions.ALLOWED_CALLER_PACKAGES
        if (callerPackages.intersect(allowed).isEmpty()) {
            Log.w(TAG, "Rejected broadcast from unauthorized caller: $callerPackages")
            return
        }

        Log.i(TAG, "Received: ${intent.action} from $callerPackages")

        when (intent.action) {
            CrossProcessActions.ACTION_SHOW_QUICK_POPUP -> {
                val popupIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.getStringExtra(CrossProcessActions.EXTRA_DEVICE_MAC)
                        ?.let { putExtra(CrossProcessActions.EXTRA_DEVICE_MAC, it) }
                }
                context.startActivity(popupIntent)
            }
            CrossProcessActions.ACTION_QUERY_DEVICE_MAC -> {
                if (!SonyControlService.controlCenterInterceptEnabled) {
                    Log.d(TAG, "QUERY_DEVICE_MAC ignored: control center intercept disabled")
                    return
                }
                val mac = SonyControlService.currentDeviceMac ?: ""
                val response = Intent(CrossProcessActions.ACTION_DEVICE_MAC_RECEIVED).apply {
                    setPackage("com.android.systemui")
                    putExtra(CrossProcessActions.EXTRA_DEVICE_MAC, mac)
                }
                context.sendBroadcast(response)
                Log.d(TAG, "QUERY_DEVICE_MAC responded with MAC: ${if (mac.isNotEmpty()) mac else "(empty)"}")
            }
        }
    }

    companion object {
        private const val TAG = "OpenBuds"
    }
}
