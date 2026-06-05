package dev.ignotus.openbuds.lsposed.xiaomi_bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MiuiSppProxyStrategyTest {
    @Test
    fun strategyLabel_mapsKnownTransports() {
        assertEquals("pc", MiuiSppProxyStrategySelector.strategyLabel(MiuiSppProxyTransport.PC))
        assertEquals("direct", MiuiSppProxyStrategySelector.strategyLabel(MiuiSppProxyTransport.DIRECT))
    }

    @Test
    fun linkBudsSGattProxy_isExplicitlyUnsupported() {
        assertFalse(MiuiGattProxyStrategySupport.isSupportedForLinkBudsS())
        assertEquals(
            "linkbuds_s_uses_sony_spp_not_gatt",
            MiuiGattProxyStrategySupport.LINKBUDS_S_UNSUPPORTED_REASON,
        )
    }
}
