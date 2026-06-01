package dev.ignotus.openbuds.ble.sony

import dev.ignotus.openbuds.protocol.sony.SonyGatt
import java.util.UUID

data class PendingTandemWrite(
    val channel: SonyChannel,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingTandemWrite) return false
        return channel == other.channel && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * channel.hashCode() + bytes.contentHashCode()
}

data class TandemGattEndpointSpec(
    val channel: SonyChannel,
    val serviceUuid: UUID,
    val toAccUuid: UUID,
    val fromAccUuid: UUID,
)

object TandemGattRouting {
    /**
     * Sony GATT channels that have Tandem endpoints.
     */
    val SONY_GATT_CHANNELS: Set<SonyChannel> = setOf(
        SonyChannel.GATT_V2_HPC,
        SonyChannel.GATT_V2_MC,
        SonyChannel.GATT_V1_MC,
    )

    private val endpointSpecs: Map<SonyChannel, TandemGattEndpointSpec> = mapOf(
        SonyChannel.GATT_V2_HPC to TandemGattEndpointSpec(
            channel = SonyChannel.GATT_V2_HPC,
            serviceUuid = SonyGatt.TANDEM_V2_HPC_SERVICE,
            toAccUuid = SonyGatt.TANDEM_HPC_TO_ACC,
            fromAccUuid = SonyGatt.TANDEM_HPC_FROM_ACC,
        ),
        SonyChannel.GATT_V2_MC to TandemGattEndpointSpec(
            channel = SonyChannel.GATT_V2_MC,
            serviceUuid = SonyGatt.TANDEM_V2_MC_SERVICE,
            toAccUuid = SonyGatt.TANDEM_MC_TO_ACC,
            fromAccUuid = SonyGatt.TANDEM_MC_FROM_ACC,
        ),
        SonyChannel.GATT_V1_MC to TandemGattEndpointSpec(
            channel = SonyChannel.GATT_V1_MC,
            serviceUuid = SonyGatt.TANDEM_V1_MC_SERVICE,
            toAccUuid = SonyGatt.TANDEM_MC_TO_ACC,
            fromAccUuid = SonyGatt.TANDEM_MC_FROM_ACC,
        ),
    )

    private val gattNotificationOrder = mapOf(
        SonyChannel.GATT_V2_HPC to 0,
        SonyChannel.GATT_V2_MC to 1,
        SonyChannel.GATT_V1_MC to 2,
    )

    fun endpointSpecFor(channel: SonyChannel): TandemGattEndpointSpec =
        endpointSpecs[channel] ?: error("No Tandem GATT endpoint for $channel")

    fun notificationOrder(channels: Iterable<SonyChannel>): List<SonyChannel> =
        channels
            .filter { it in gattNotificationOrder }
            .sortedBy { gattNotificationOrder.getValue(it) }

    fun fromAccChannelFor(serviceUuid: UUID?, characteristicUuid: UUID?): SonyChannel? {
        if (serviceUuid == null || characteristicUuid == null) return null
        return SONY_GATT_CHANNELS
            .firstOrNull { channel ->
                val spec = endpointSpecFor(channel)
                spec.serviceUuid == serviceUuid && spec.fromAccUuid == characteristicUuid
            }
    }

    fun fromAccChannel(
        endpoints: Map<SonyChannel, GattTandemEndpoint>,
        serviceUuid: UUID?,
        characteristicUuid: UUID?,
    ): SonyChannel? =
        fromAccChannelFor(serviceUuid, characteristicUuid)?.takeIf { it in endpoints }
            ?: endpoints.values
                .filter { it.fromAcc.uuid == characteristicUuid }
                .singleOrNull()
                ?.channel
}
