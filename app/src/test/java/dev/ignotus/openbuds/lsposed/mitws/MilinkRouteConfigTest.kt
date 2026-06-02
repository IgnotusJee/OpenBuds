package dev.ignotus.openbuds.lsposed.mitws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class MilinkRouteConfigTest {

    @Test
    fun mode_defaultsToTraceOnly_whenSystemPropertyNotSet() {
        // SystemProperties.get() is an Android API unavailable in unit tests;
        // the reflection fallback returns the default "false", so mode is TRACE_ONLY.
        assertEquals(MilinkRouteMode.TRACE_ONLY, MilinkRouteConfig.mode())
    }

    @Test
    fun isSystemFacadeEnabled_defaultsToFalse() {
        assertFalse(MilinkRouteConfig.isSystemFacadeEnabled())
    }

    @Test
    fun canUseFacade_returnsFalse_whenBridgeClientIsNull() {
        assertFalse(MilinkRouteConfig.canUseFacade(null))
    }

    @Test
    fun selectedDeviceIdTemplate_defaultsToGeneric() {
        // SystemProperties fallback returns "generic" → genericEarbudTemplate
        assertSame(
            MiTwsDeviceIdPolicy.genericEarbudTemplate,
            MilinkRouteConfig.selectedDeviceIdTemplate(),
        )
    }

    @Test
    fun allowOpenBudsMmaPassthrough_defaultsToFalse() {
        assertFalse(MilinkRouteConfig.allowOpenBudsMmaPassthrough())
    }

    @Test
    fun mitwsEnableProperty_constantIsCorrect() {
        assertEquals(
            "debug.openbuds.milink_mitws_enable",
            MilinkRouteConfig.MITWS_ENABLE_PROPERTY,
        )
    }

    @Test
    fun mitwsDeviceIdProperty_constantIsCorrect() {
        assertEquals(
            "debug.openbuds.milink_mitws_device_id",
            MilinkRouteConfig.MITWS_DEVICE_ID_PROPERTY,
        )
    }

    @Test
    fun mitwsMmaPassthroughProperty_constantIsCorrect() {
        assertEquals(
            "debug.openbuds.milink_mitws_mma_passthrough",
            MilinkRouteConfig.MITWS_MMA_PASSTHROUGH_PROPERTY,
        )
    }
}
