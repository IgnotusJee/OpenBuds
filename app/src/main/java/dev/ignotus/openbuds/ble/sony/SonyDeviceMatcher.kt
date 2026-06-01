package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.headphones.TandemChannel
import java.util.UUID

object SonyDeviceMatcher {
    fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean {
        val name = (reportedModelName ?: device.name).lowercase()
        if (name.contains("qcy")) return false
        val sonyAd = device.sonyAd != null
        val sonyServices = device.advertisedServices.any(::isSonyTandemService)
        return sonyAd || sonyServices || isHeadphoneCandidate(name)
    }

    fun isHeadphoneCandidate(name: String?): Boolean {
        val normalized = name?.trim()?.lowercase().orEmpty()
        return normalized.contains("sony") ||
            normalized.contains("linkbuds") ||
            normalized.contains("qcy") ||
            normalized.startsWith("wf-") ||
            normalized.startsWith("wh-") ||
            normalized.startsWith("wi-") ||
            normalized.startsWith("xba-") ||
            normalized.startsWith("mdr-")
    }

    private fun isSonyTandemService(value: String): Boolean {
        val channelFromUuid = runCatching {
            TandemChannel.fromServiceUuid(UUID.fromString(value))
        }.getOrNull()
        if (channelFromUuid != null) return true
        return value == "TANDEM_V2_HPC_SERVICE" ||
            value == "TANDEM_V2_MC_SERVICE" ||
            value == "TANDEM_V1_MC_SERVICE"
    }
}
