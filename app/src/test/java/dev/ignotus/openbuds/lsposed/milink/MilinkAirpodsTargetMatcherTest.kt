package dev.ignotus.openbuds.lsposed.milink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

}
