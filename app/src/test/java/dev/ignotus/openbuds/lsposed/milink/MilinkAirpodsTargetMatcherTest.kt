package dev.ignotus.openbuds.lsposed.milink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MilinkAirpodsTargetMatcherTest {

    @Test
    fun normalizeMac_acceptsUpperAndLowerCaseColonFormat() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            MilinkAirpodsTargetMatcher.normalizeMac(" aa:bb:cc:dd:ee:ff "),
        )
    }

    @Test
    fun normalizeMac_rejectsIncompleteOrDashSeparatedValues() {
        assertNull(MilinkAirpodsTargetMatcher.normalizeMac("AA:BB:CC:DD:EE"))
        assertNull(MilinkAirpodsTargetMatcher.normalizeMac("AA-BB-CC-DD-EE-FF"))
        assertNull(MilinkAirpodsTargetMatcher.normalizeMac(""))
        assertNull(MilinkAirpodsTargetMatcher.normalizeMac(null))
    }

    @Test
    fun parseMacList_ignoresInvalidValuesAndDeduplicates() {
        assertEquals(
            setOf("AA:BB:CC:DD:EE:FF", "00:11:22:33:44:55"),
            MilinkAirpodsTargetMatcher.parseMacList(
                "aa:bb:cc:dd:ee:ff,invalid;00:11:22:33:44:55 AA:BB:CC:DD:EE:FF",
            ),
        )
    }

    @Test
    fun configuredTargets_usesDefaultWhenPropertyEmptyOrInvalid() {
        assertEquals(
            setOf("00:11:22:33:44:55"),
            MilinkAirpodsTargetMatcher.configuredTargets(null),
        )
        assertEquals(
            setOf("00:11:22:33:44:55"),
            MilinkAirpodsTargetMatcher.configuredTargets("not-a-mac"),
        )
    }

    @Test
    fun isTargetMac_matchesOnlyConfiguredMacs() {
        val configured = "AA:BB:CC:DD:EE:FF"

        assertTrue(MilinkAirpodsTargetMatcher.isTargetMac("aa:bb:cc:dd:ee:ff", configured))
        assertFalse(MilinkAirpodsTargetMatcher.isTargetMac("00:11:22:33:44:55", configured))
        assertFalse(MilinkAirpodsTargetMatcher.isTargetMac("not-a-mac", configured))
    }

    @Test
    fun airpodsDecision_preservesTrueOriginalResult() {
        assertTrue(
            MilinkAirpodsTargetMatcher.airpodsDecision(
                originalResult = true,
                mac = "12:34:56:78:9A:BC",
                debugPropertyValue = null,
            ),
        )
    }

    @Test
    fun airpodsDecision_overridesFalseOriginalOnlyForTargetMac() {
        val configured = "AA:BB:CC:DD:EE:FF"

        assertTrue(
            MilinkAirpodsTargetMatcher.airpodsDecision(
                originalResult = false,
                mac = "aa:bb:cc:dd:ee:ff",
                debugPropertyValue = configured,
            ),
        )
        assertFalse(
            MilinkAirpodsTargetMatcher.airpodsDecision(
                originalResult = false,
                mac = "12:34:56:78:9A:BC",
                debugPropertyValue = configured,
            ),
        )
    }
}
