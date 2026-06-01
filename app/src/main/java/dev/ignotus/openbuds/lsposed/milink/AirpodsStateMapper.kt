package dev.ignotus.openbuds.lsposed.milink

import dev.ignotus.openbuds.integration.milink.MilinkDeviceSnapshot

/**
 * Converts OpenBuds bridge snapshots into MiLink's AirPods state formats.
 *
 * MiLink consumes two related but not identical shapes:
 * - `MxBluetoothManager.getAirPodsState(mac)` returns a 9-element `String[]`.
 * - `ContentResolver.call(..., "getAirpodsState", mac, null)` returns an
 *   11-key Bundle. The hook converts [toBundleFields] into the Bundle.
 */
object AirpodsStateMapper {
    const val CONNECTED_STATE = "2"
    const val DISCONNECTED_STATE = "0"

    fun fromMilinkSnapshot(snapshot: MilinkDeviceSnapshot): AirpodsStateSnapshot {
        val leftBattery = snapshot.leftBattery ?: snapshot.singleBattery
        val rightBattery = snapshot.rightBattery ?: snapshot.singleBattery
        return AirpodsStateSnapshot(
            mac = MilinkAirpodsTargetMatcher.normalizeMac(snapshot.mac) ?: snapshot.mac,
            deviceId = snapshot.deviceId.ifBlank { DeviceIdRegistry.deviceIdForMac(snapshot.mac) },
            isLeftWearing = snapshot.leftWearing.toMilinkOptionalBoolean(),
            leftBattery = leftBattery.toMilinkBattery(),
            isRightWearing = snapshot.rightWearing.toMilinkOptionalBoolean(),
            rightBattery = rightBattery.toMilinkBattery(),
            boxBattery = snapshot.caseBattery.toMilinkBattery(),
            isLeftCharging = snapshot.leftCharging.toMilinkChargingBoolean(),
            isRightCharging = snapshot.rightCharging.toMilinkChargingBoolean(),
            isBoxCharging = snapshot.caseCharging.toMilinkChargingBoolean(),
            connectState = if (snapshot.connected) CONNECTED_STATE else DISCONNECTED_STATE,
        )
    }

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

    private fun Int?.toMilinkBattery(): String =
        this?.coerceIn(0, 100)?.toString() ?: "-"

    private fun Boolean?.toMilinkOptionalBoolean(): String =
        this?.toString() ?: "-"

    private fun Boolean?.toMilinkChargingBoolean(): String =
        this?.toString() ?: "false"
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
