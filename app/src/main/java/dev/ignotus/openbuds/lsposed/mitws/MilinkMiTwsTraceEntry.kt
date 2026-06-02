package dev.ignotus.openbuds.lsposed.mitws

import android.util.Log

class MilinkMiTwsTraceEntry(
    private val classLoader: ClassLoader,
) {
    fun install() {
        Log.i(TAG, "Installing MiLink MiTWS trace hooks")
        MilinkMiTwsFacadeHook(classLoader, bridgeClient = null).installTraceHooks()
    }

    private companion object {
        private const val TAG = "OpenBuds"
    }
}
