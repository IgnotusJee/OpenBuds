package dev.ignotus.openbuds.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * System integration receiver — handles cross-process intents from LSPosed module.
 * Phase 1: reserved for future MiLink service whitelist refresh triggers.
 */
class SystemIntegrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received: ${intent.action}")
        // Phase 1: no actions yet; will handle whitelist refresh triggers
    }

    companion object {
        private const val TAG = "OpenBuds"
    }
}
