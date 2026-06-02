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

    fun ancState(snapshot: MilinkDeviceSnapshot): Int =
        snapshot.ancMode?.takeIf { it in ANC_OFF..ANC_TRANSPARENT } ?: UNKNOWN_INT

    fun connected(snapshot: MilinkDeviceSnapshot): Boolean = snapshot.connected

    fun ringing(snapshot: MilinkDeviceSnapshot): Boolean = snapshot.ringing

    /**
     * Maps wearing state to MiTWS wear status string.
     *
     * Values used by AncBatteryController.isSupportOpAnc():
     * - "0" = not worn (return code 209/230)
     * - "1" = left worn (return code 100 = allowed)
     * - "2" = both worn (return code 100 = allowed)
     * - "3" = right worn (return code 100 = allowed)
     * - "-1" = unknown
     */
    fun wearStatus(snapshot: MilinkDeviceSnapshot): String {
        val left = snapshot.leftWearing
        val right = snapshot.rightWearing
        return when {
            left == true && right == true -> "2"
            left == true -> "1"
            right == true -> "3"
            left == false && right == false -> "0"
            else -> "-1"
        }
    }

    private fun Int.coerceBattery(): Int =
        if (this == UNKNOWN_INT) UNKNOWN_INT else coerceIn(0, 100)
}
