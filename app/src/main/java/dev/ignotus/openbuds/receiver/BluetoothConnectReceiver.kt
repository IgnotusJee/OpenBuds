package dev.ignotus.openbuds.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.ignotus.openbuds.service.SonyControlService

/**
 * Listens for [BluetoothDevice.ACTION_ACL_CONNECTED] and triggers
 * auto-connect via [SonyControlService] so that the OpenBuds protocol
 * connection is established immediately when a known headphone pairs.
 *
 * This is the bridge between "system Bluetooth connected" and
 * "app has protocol data for milink to consume".
 */
class BluetoothConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return

        val name = device.name ?: return
        Log.i(TAG, "Bluetooth ACL connected: $name (${device.address})")

        val svc = Intent(context, SonyControlService::class.java).apply {
            action = SonyControlService.ACTION_AUTO_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }

    companion object {
        private const val TAG = "OpenBuds"
    }
}
