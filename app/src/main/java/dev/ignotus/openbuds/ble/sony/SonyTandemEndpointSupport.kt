package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.protocol.sony.SonyGatt
import java.util.UUID

object SonyTandemEndpointSupport {
    val supportedControlServices: Set<UUID> = setOf(
        SonyGatt.TANDEM_V2_HPC_SERVICE,
        SonyGatt.TANDEM_V1_MC_SERVICE,
    )

    fun supportState(services: Collection<UUID>): String? =
        if (services.any { it in supportedControlServices }) null else unsupportedReason(services)

    fun unsupportedReason(services: Collection<UUID>): String {
        val labels = services.map { SonyGatt.serviceLabel(it) }
        return when {
            SonyGatt.TANDEM_V1_MC_SERVICE in services ->
                "Tandem V1 MC service was found, but no usable MC control endpoint could be registered. Services: ${labels.joinToString()}"
            SonyGatt.LE_AUDIO_CAPABILITY_FOR_HPC in services ->
                "This LE endpoint exposes LE Audio capability, not Tandem V2 HPC control. Try disabling LE Audio / using classic-only mode, then rescan."
            SonyGatt.BLUETOOTH_PAIRING_COMPLETE_NAME_SERVICE in services ->
                "This LE endpoint is a pairing/name endpoint, not Tandem V2 HPC control. Services: ${labels.joinToString()}"
            else -> "Tandem control service was not found. Services: ${labels.joinToString()}"
        }
    }
}
