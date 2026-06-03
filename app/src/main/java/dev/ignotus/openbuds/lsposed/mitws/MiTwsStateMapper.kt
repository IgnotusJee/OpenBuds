package dev.ignotus.openbuds.lsposed.mitws

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot
object MiTwsStateMapper {
    const val ANC_OFF = 0
    const val ANC_NOISE_CANCELLING = 1
    const val ANC_TRANSPARENT = 2
    const val UNKNOWN_INT = -1

    fun batteryArray(snapshot: MilinkDeviceSnapshot): IntArray {
        val single = snapshot.singleBattery
        val left = snapshot.leftBattery ?: single ?: UNKNOWN_INT
        val right = snapshot.rightBattery ?: single ?: UNKNOWN_INT
        val case = snapshot.caseBattery ?: UNKNOWN_INT
        return intArrayOf(left.coerceBattery(), right.coerceBattery(), case.coerceBattery())
    }

    fun ancState(snapshot: MilinkDeviceSnapshot): Int {
        val mode = snapshot.ancMode
        if (mode != null && mode in ANC_OFF..ANC_TRANSPARENT) return mode
        // When ancMode is unknown but the device supports noise control, return OFF (0)
        // instead of UNKNOWN_INT (-1). AncBatteryController.getAncState() calls
        // ancBatteryModel.setAncState() with our return value — returning -1 would
        // overwrite the model's correct ANC state (set by onAncStateChanged callback)
        // and cause isSupportOpAnc() to return 207 (not supported), blocking ANC switch.
        if (snapshot.supportsNoiseControl) return ANC_OFF
        return UNKNOWN_INT
    }

    fun connected(snapshot: MilinkDeviceSnapshot): Boolean = snapshot.connected

    fun ringing(snapshot: MilinkDeviceSnapshot): Boolean = snapshot.ringing

    /**
     * Maps wearing state to MiTWS wear status string.
     *
     * Values used by AncBatteryController.isSupportOpAnc():
     * Non-Flora device template (01010101) only allows "1":
     * - "0" = not worn → return code 209
     * - "1" = left worn → return code 100 (allowed)
     * - "2" = both worn → return code 210 (NOT allowed)
     * - "3" = right worn → return code 211 (NOT allowed)
     * Flora template allows "1", "2", "3" → return 100.
     *
     * To avoid blocking ANC on the default generic template, we collapse
     * any worn state into "1".
     */
    fun wearStatus(snapshot: MilinkDeviceSnapshot): String {
        val left = snapshot.leftWearing
        val right = snapshot.rightWearing
        // When wearing state is unknown but the device supports ANC, return "1"
        // (worn). AncBatteryController.isSupportOpAnc() checks wear status and
        // returns 212 (not 100) for "-1" (unknown), blocking ANC switching.
        return when {
            left == true || right == true -> "1"
            left == false && right == false -> "0"
            snapshot.supportsNoiseControl -> "1"
            else -> "-1"
        }
    }

    private fun Int.coerceBattery(): Int =
        if (this == UNKNOWN_INT) UNKNOWN_INT else coerceIn(0, 100)
}
