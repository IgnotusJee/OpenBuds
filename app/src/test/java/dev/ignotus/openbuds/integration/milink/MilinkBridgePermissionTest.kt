package dev.ignotus.openbuds.integration.milink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MilinkBridgePermissionTest {

    // ---------------------------------------------------------------------------
    //  CallerVerifier
    // ---------------------------------------------------------------------------

    @Test
    fun isAllowedUid_acceptsOwnUid() {
        val verifier = verifier(ownUid = 10, packages = emptyMap())
        assertTrue(verifier.isAllowedUid(10))
    }

    @Test
    fun isAllowedUid_acceptsMilinkServicePackage() {
        val verifier = verifier(
            packages = mapOf(20 to arrayOf(MilinkBridgeContract.MILINK_PACKAGE)),
        )
        assertTrue(verifier.isAllowedUid(20))
    }

    @Test
    fun isAllowedUid_acceptsOpenBudsPackage() {
        val verifier = verifier(
            packages = mapOf(21 to arrayOf(MilinkBridgeContract.OPENBUDS_PACKAGE)),
        )
        assertTrue(verifier.isAllowedUid(21))
    }

    @Test
    fun isAllowedUid_rejectsUnknownPackage() {
        val verifier = verifier(
            packages = mapOf(30 to arrayOf("com.example.other")),
        )
        assertFalse(verifier.isAllowedUid(30))
    }

    @Test(expected = SecurityException::class)
    fun verify_throwsSecurityExceptionForUnknownUid() {
        verifier(packages = mapOf(30 to arrayOf("com.example.other"))).verify(30)
    }

    @Test
    fun verify_doesNotThrowForAllowedUids() {
        val vOwn = verifier(ownUid = 1, packages = emptyMap())
        vOwn.verify(1) // own UID — must not throw

        val vMilink = verifier(packages = mapOf(2 to arrayOf(MilinkBridgeContract.MILINK_PACKAGE)))
        vMilink.verify(2) // milink package — must not throw

        val vOpenBuds = verifier(packages = mapOf(3 to arrayOf(MilinkBridgeContract.OPENBUDS_PACKAGE)))
        vOpenBuds.verify(3) // openbuds package — must not throw
    }

    // ---------------------------------------------------------------------------
    //  MAC normalisation
    // ---------------------------------------------------------------------------

    @Test
    fun normalizeMac_validColonSeparated_returnsUppercase() {
        assertEquals("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff".normalizeMac())
    }

    @Test
    fun normalizeMac_lowercase_isUppercased() {
        assertEquals("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff".normalizeMac())
    }

    @Test
    fun normalizeMac_whitespace_isTrimmed() {
        assertEquals("AA:BB:CC:DD:EE:FF", "  aa:bb:cc:dd:ee:ff  ".normalizeMac())
    }

    @Test
    fun normalizeMac_nonColonMac_returnsNull() {
        assertNull("AABBCCDDEEFF".normalizeMac())
    }

    @Test
    fun normalizeMac_blank_returnsNull() {
        assertNull("".normalizeMac())
        assertNull("   ".normalizeMac())
    }

    @Test
    fun normalizeMac_garbage_returnsNull() {
        assertNull("not a mac".normalizeMac())
    }

    @Test
    fun normalizeMac_mixedCase_returnsUppercase() {
        assertEquals("AA:BB:CC:DD:EE:FF", "aA:bB:cC:dD:eE:fF".normalizeMac())
    }

    // Bundle roundtrip tests require Robolectric — skipped in plain JVM unit tests.
    // The serialization correctness is covered by MilinkBridgeSnapshotMapperTest.

    // ---------------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------------

    private fun verifier(
        ownUid: Int = 1,
        packages: Map<Int, Array<String>>,
    ): MilinkBridgeCallerVerifier =
        MilinkBridgeCallerVerifier(
            ownUid = { ownUid },
            packageResolver = { uid -> packages[uid].orEmpty() },
        )
}
