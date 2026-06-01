package dev.ignotus.openbuds.integration.milink

import android.os.Bundle
import dev.ignotus.openbuds.data.HeadphoneUiState
import dev.ignotus.openbuds.lsposed.milink.DeviceIdRegistry

data class MilinkDeviceSnapshot(
    val mac: String,
    val name: String?,
    val brand: String?,
    val model: String?,
    val deviceId: String,
    val connected: Boolean,
    val protocolReady: Boolean,
    val leftBattery: Int?,
    val rightBattery: Int?,
    val caseBattery: Int?,
    val singleBattery: Int?,
    val leftWearing: Boolean?,
    val rightWearing: Boolean?,
    val leftCharging: Boolean?,
    val rightCharging: Boolean?,
    val caseCharging: Boolean?,
    val revision: Long,
    val updatedAt: Long,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(MilinkBridgeContract.KEY_MAC, mac)
        name?.let { putString(MilinkBridgeContract.KEY_NAME, it) }
        brand?.let { putString(MilinkBridgeContract.KEY_BRAND, it) }
        model?.let { putString(MilinkBridgeContract.KEY_MODEL, it) }
        putString(MilinkBridgeContract.KEY_DEVICE_ID, deviceId)
        putBoolean(MilinkBridgeContract.KEY_CONNECTED, connected)
        putBoolean(MilinkBridgeContract.KEY_PROTOCOL_READY, protocolReady)
        leftBattery?.let { putInt(MilinkBridgeContract.KEY_LEFT_BATTERY, it) }
        rightBattery?.let { putInt(MilinkBridgeContract.KEY_RIGHT_BATTERY, it) }
        caseBattery?.let { putInt(MilinkBridgeContract.KEY_CASE_BATTERY, it) }
        singleBattery?.let { putInt(MilinkBridgeContract.KEY_SINGLE_BATTERY, it) }
        leftWearing?.let { putBoolean(MilinkBridgeContract.KEY_LEFT_WEARING, it) }
        rightWearing?.let { putBoolean(MilinkBridgeContract.KEY_RIGHT_WEARING, it) }
        leftCharging?.let { putBoolean(MilinkBridgeContract.KEY_LEFT_CHARGING, it) }
        rightCharging?.let { putBoolean(MilinkBridgeContract.KEY_RIGHT_CHARGING, it) }
        caseCharging?.let { putBoolean(MilinkBridgeContract.KEY_CASE_CHARGING, it) }
        putLong(MilinkBridgeContract.KEY_REVISION, revision)
        putLong(MilinkBridgeContract.KEY_UPDATED_AT, updatedAt)
    }

    companion object {
        fun fromBundle(bundle: Bundle?): MilinkDeviceSnapshot? {
            if (bundle == null) return null
            val mac = bundle.getString(MilinkBridgeContract.KEY_MAC)?.normalizeMac() ?: return null
            return MilinkDeviceSnapshot(
                mac = mac,
                name = bundle.getString(MilinkBridgeContract.KEY_NAME),
                brand = bundle.getString(MilinkBridgeContract.KEY_BRAND),
                model = bundle.getString(MilinkBridgeContract.KEY_MODEL),
                deviceId = bundle.getString(MilinkBridgeContract.KEY_DEVICE_ID)
                    ?: DeviceIdRegistry.GENERIC_EARBUD_DEVICE_ID,
                connected = bundle.getBoolean(MilinkBridgeContract.KEY_CONNECTED, false),
                protocolReady = bundle.getBoolean(MilinkBridgeContract.KEY_PROTOCOL_READY, false),
                leftBattery = bundle.intOrNull(MilinkBridgeContract.KEY_LEFT_BATTERY),
                rightBattery = bundle.intOrNull(MilinkBridgeContract.KEY_RIGHT_BATTERY),
                caseBattery = bundle.intOrNull(MilinkBridgeContract.KEY_CASE_BATTERY),
                singleBattery = bundle.intOrNull(MilinkBridgeContract.KEY_SINGLE_BATTERY),
                leftWearing = bundle.booleanOrNull(MilinkBridgeContract.KEY_LEFT_WEARING),
                rightWearing = bundle.booleanOrNull(MilinkBridgeContract.KEY_RIGHT_WEARING),
                leftCharging = bundle.booleanOrNull(MilinkBridgeContract.KEY_LEFT_CHARGING),
                rightCharging = bundle.booleanOrNull(MilinkBridgeContract.KEY_RIGHT_CHARGING),
                caseCharging = bundle.booleanOrNull(MilinkBridgeContract.KEY_CASE_CHARGING),
                revision = bundle.getLong(MilinkBridgeContract.KEY_REVISION, 0L),
                updatedAt = bundle.getLong(MilinkBridgeContract.KEY_UPDATED_AT, 0L),
            )
        }
    }
}

object MilinkBridgeSnapshotMapper {
    fun fromUiState(
        state: HeadphoneUiState,
        revision: Long,
        updatedAt: Long,
    ): MilinkDeviceSnapshot? {
        val device = state.connectedDevice ?: return null
        val mac = device.address.normalizeMac() ?: return null
        val battery = state.batteryState
        val profile = state.connectedProfile
        return MilinkDeviceSnapshot(
            mac = mac,
            name = device.name.takeIf { it.isNotBlank() } ?: profile?.displayName,
            brand = profile?.brand,
            model = state.deviceInfo.modelName ?: profile?.modelName,
            deviceId = DeviceIdRegistry.deviceIdForMac(mac),
            connected = true,
            protocolReady = state.deviceInfo.protocolReady,
            leftBattery = battery.left,
            rightBattery = battery.right,
            caseBattery = battery.cradle,
            singleBattery = battery.single,
            leftWearing = state.wearingState.leftWearing,
            rightWearing = state.wearingState.rightWearing,
            leftCharging = battery.leftCharging,
            rightCharging = battery.rightCharging,
            caseCharging = battery.cradleCharging,
            revision = revision,
            updatedAt = updatedAt,
        )
    }
}

fun String.normalizeMac(): String? {
    val normalized = trim().uppercase()
    return normalized.takeIf { MAC_REGEX.matches(it) }
}

private fun Bundle.intOrNull(key: String): Int? =
    if (containsKey(key)) getInt(key).coerceIn(0, 100) else null

private fun Bundle.booleanOrNull(key: String): Boolean? =
    if (containsKey(key)) getBoolean(key) else null

private val MAC_REGEX = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
