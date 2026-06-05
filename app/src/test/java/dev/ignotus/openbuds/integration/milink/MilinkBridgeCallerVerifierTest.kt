package dev.ignotus.openbuds.integration.milink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MilinkBridgeCallerVerifierTest {

    @Test
    fun isAllowedUid_acceptsOwnUid() {
        val verifier = verifier(ownUid = 10, packages = emptyMap())

        assertTrue(verifier.isAllowedUid(10))
    }

    @Test
    fun isAllowedUid_acceptsMilinkPackage() {
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
    fun clientRole_rejectsXiaomiBluetoothPackage() {
        val verifier = verifier(
            packages = mapOf(22 to arrayOf(MilinkBridgeContract.XIAOMI_BLUETOOTH_PACKAGE)),
        )

        assertFalse(verifier.isAllowedUid(22, MilinkBridgeCallerRole.CLIENT))
    }

    @Test
    fun transportProxyRole_acceptsXiaomiBluetoothAndOpenBudsOnly() {
        val verifier = verifier(
            packages = mapOf(
                22 to arrayOf(MilinkBridgeContract.XIAOMI_BLUETOOTH_PACKAGE),
                23 to arrayOf(MilinkBridgeContract.OPENBUDS_PACKAGE),
                24 to arrayOf(MilinkBridgeContract.MILINK_PACKAGE),
            ),
        )

        assertTrue(verifier.isAllowedUid(22, MilinkBridgeCallerRole.TRANSPORT_PROXY))
        assertTrue(verifier.isAllowedUid(23, MilinkBridgeCallerRole.TRANSPORT_PROXY))
        assertFalse(verifier.isAllowedUid(24, MilinkBridgeCallerRole.TRANSPORT_PROXY))
    }

    @Test
    fun isAllowedUid_rejectsUnknownPackage() {
        val verifier = verifier(
            packages = mapOf(30 to arrayOf("com.example.other")),
        )

        assertFalse(verifier.isAllowedUid(30))
    }

    @Test(expected = SecurityException::class)
    fun verify_throwsForUnknownPackage() {
        verifier(packages = mapOf(30 to arrayOf("com.example.other"))).verify(30)
    }

    private fun verifier(
        ownUid: Int = 1,
        packages: Map<Int, Array<String>>,
    ): MilinkBridgeCallerVerifier =
        MilinkBridgeCallerVerifier(
            ownUid = { ownUid },
            packageResolver = { uid -> packages[uid].orEmpty() },
        )
}
