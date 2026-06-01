package dev.ignotus.openbuds.integration.milink

object MilinkBridgeContract {
    const val ACTION_BIND = "dev.ignotus.openbuds.action.BIND_MILINK_BRIDGE"
    const val SERVICE_PACKAGE = "dev.ignotus.openbuds"
    const val SERVICE_CLASS = "dev.ignotus.openbuds.integration.milink.MilinkBridgeService"
    const val MILINK_PACKAGE = "com.milink.service"
    const val OPENBUDS_PACKAGE = "dev.ignotus.openbuds"

    const val KEY_TOKEN = "token"
    const val KEY_ENABLED = "enabled"
    const val KEY_CONNECTED = "connected"
    const val KEY_PROTOCOL_READY = "protocolReady"
    const val KEY_AUTHORIZED_MACS = "authorizedMacs"
    const val KEY_REASON = "reason"
    const val KEY_LAST_ERROR = "lastError"

    const val KEY_MAC = "mac"
    const val KEY_NAME = "name"
    const val KEY_BRAND = "brand"
    const val KEY_MODEL = "model"
    const val KEY_DEVICE_ID = "deviceId"
    const val KEY_LEFT_BATTERY = "leftBattery"
    const val KEY_RIGHT_BATTERY = "rightBattery"
    const val KEY_CASE_BATTERY = "caseBattery"
    const val KEY_SINGLE_BATTERY = "singleBattery"
    const val KEY_LEFT_WEARING = "leftWearing"
    const val KEY_RIGHT_WEARING = "rightWearing"
    const val KEY_LEFT_CHARGING = "leftCharging"
    const val KEY_RIGHT_CHARGING = "rightCharging"
    const val KEY_CASE_CHARGING = "caseCharging"
    const val KEY_REVISION = "revision"
    const val KEY_UPDATED_AT = "updatedAt"
}
