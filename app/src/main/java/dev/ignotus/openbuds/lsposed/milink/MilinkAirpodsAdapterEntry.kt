package dev.ignotus.openbuds.lsposed.milink

import android.util.Log

/**
 * Top-level entry point for the MiLink first-party adapter path.
 *
 * ## Architecture
 *
 * This entry is invoked by [ModuleMain][dev.ignotus.openbuds.lsposed.ModuleMain]
 * when the LSPosed module loads inside `com.milink.service`. It installs hooks
 * that intercept milink's AirPods classification chain **before** any binder
 * call reaches `com.android.bluetooth`.
 *
 * ## Milestone progression
 *
 * | Phase | What's installed | State visibility |
 * |-------|-----------------|-----------------|
 * | M1    | [MilinkAirpodsM1Hook] — classification hooks (`checkIsAirPods`, `isAirPods`) + trace hooks (`getAirpodsDeviceId`, `getAirpodsHeadsetType`) + ContentResolver state fake | Placeholder bundle (static values) |
 * | M3    | Bridge to OpenBuds App — real battery/ANC/wearing state via IPC | Live state from [HeadphoneRepository] |
 *
 * ## Key principle
 *
 * **Never cross the process boundary.** All hooks live inside `com.milink.service`.
 * The Bluetooth process (`com.android.bluetooth`) is never touched, hooked, or
 * reverse-engineered.
 *
 * @see MilinkAirpodsM1Hook for hook implementation details
 * @see docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN.md
 */
class MilinkAirpodsAdapterEntry(
    private val classLoader: ClassLoader,
) {
    fun install() {
        Log.i(TAG, "Installing MiLink AirPods M1 hooks")
        MilinkAirpodsM1Hook(classLoader).install()
    }

    private companion object {
        private const val TAG = "OpenBuds"
    }
}
