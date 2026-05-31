package dev.ignotus.openbuds.lsposed.milink

/**
 * Converts OpenBuds/M2 placeholder state into MiLink's AirPods state formats.
 *
 * MiLink consumes two related but not identical shapes:
 * - `MxBluetoothManager.getAirPodsState(mac)` returns a 9-element `String[]`.
 * - `ContentResolver.call(..., "getAirpodsState", mac, null)` returns an
 *   11-key Bundle. The hook converts [toBundleFields] into the Bundle.
 */
object AirpodsStateMapper {
    const val CONNECTED_STATE = "2"

    fun placeholder(mac: String?): AirpodsStateSnapshot =
        AirpodsStateSnapshot(
            mac = MilinkAirpodsTargetMatcher.normalizeMac(mac) ?: mac.orEmpty(),
            deviceId = DeviceIdRegistry.deviceIdForMac(mac),
            isLeftWearing = "true",
            leftBattery = "75",
            isRightWearing = "true",
            rightBattery = "80",
            boxBattery = "90",
            isLeftCharging = "false",
            isRightCharging = "false",
            isBoxCharging = "false",
            connectState = CONNECTED_STATE,
        )

    fun toStateArray(snapshot: AirpodsStateSnapshot): Array<String> =
        arrayOf(
            snapshot.isLeftWearing,
            snapshot.leftBattery,
            snapshot.isRightWearing,
            snapshot.rightBattery,
            snapshot.boxBattery,
            snapshot.isLeftCharging,
            snapshot.isRightCharging,
            snapshot.isBoxCharging,
            snapshot.deviceId,
        )

    fun toBundleFields(snapshot: AirpodsStateSnapshot): Map<String, String> =
        linkedMapOf(
            "device" to snapshot.mac,
            "connectState" to snapshot.connectState,
            "isLeftWearing" to snapshot.isLeftWearing,
            "leftBattery" to snapshot.leftBattery,
            "isRightWearing" to snapshot.isRightWearing,
            "rightBattery" to snapshot.rightBattery,
            "boxBattery" to snapshot.boxBattery,
            "isLeftCharging" to snapshot.isLeftCharging,
            "isRightCharging" to snapshot.isRightCharging,
            "isBoxCharging" to snapshot.isBoxCharging,
            "modelName" to snapshot.deviceId,
        )
}

data class AirpodsStateSnapshot(
    val mac: String,
    val deviceId: String,
    val isLeftWearing: String,
    val leftBattery: String,
    val isRightWearing: String,
    val rightBattery: String,
    val boxBattery: String,
    val isLeftCharging: String,
    val isRightCharging: String,
    val isBoxCharging: String,
    val connectState: String,
)
