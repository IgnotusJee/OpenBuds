package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import dev.ignotus.openbuds.protocol.NoiseControlMode

interface MiuiSppProxyStrategy {
    val transport: MiuiSppProxyTransport
    fun start(): Boolean
    fun sendNoiseControlMode(mode: NoiseControlMode): Boolean
    fun close()
}

object MiuiSppProxyStrategySelector {
    fun strategyLabel(transport: MiuiSppProxyTransport): String =
        when (transport) {
            MiuiSppProxyTransport.PC -> "pc"
            MiuiSppProxyTransport.DIRECT -> "direct"
        }
}

object MiuiGattProxyStrategySupport {
    const val LINKBUDS_S_UNSUPPORTED_REASON = "linkbuds_s_uses_sony_spp_not_gatt"

    fun isSupportedForLinkBudsS(): Boolean = false
}

class MiuiGattProxyStrategy(
    private val logger: XiaomiBluetoothTraceLogger,
    private val targetMac: String,
) {
    fun start(): Boolean {
        logger.info(
            event = "spp_proxy_gatt_unsupported",
            mac = targetMac,
            details = "reason=${MiuiGattProxyStrategySupport.LINKBUDS_S_UNSUPPORTED_REASON}",
            force = true,
        )
        return false
    }
}
