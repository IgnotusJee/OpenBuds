package dev.ignotus.openbuds.integration.milink

import android.content.Context
import android.os.Process

class MilinkBridgeCallerVerifier(
    private val ownUid: () -> Int,
    private val packageResolver: (Int) -> Array<out String>,
) {
    constructor(context: Context) : this(
        ownUid = Process::myUid,
        packageResolver = { uid -> context.packageManager.getPackagesForUid(uid).orEmpty() },
    )

    fun verify(
        uid: Int,
        role: MilinkBridgeCallerRole = MilinkBridgeCallerRole.CLIENT,
    ) {
        if (!isAllowedUid(uid, role)) {
            throw SecurityException("MiLink bridge caller uid=$uid is not allowed for role=$role")
        }
    }

    fun isAllowedUid(
        uid: Int,
        role: MilinkBridgeCallerRole = MilinkBridgeCallerRole.CLIENT,
    ): Boolean {
        if (uid == ownUid()) return true
        val packages = packageResolver(uid).toSet()
        return when (role) {
            MilinkBridgeCallerRole.CLIENT ->
                MilinkBridgeContract.MILINK_PACKAGE in packages ||
                    MilinkBridgeContract.OPENBUDS_PACKAGE in packages
            MilinkBridgeCallerRole.TRANSPORT_PROXY ->
                MilinkBridgeContract.XIAOMI_BLUETOOTH_PACKAGE in packages ||
                    MilinkBridgeContract.OPENBUDS_PACKAGE in packages
        }
    }
}

enum class MilinkBridgeCallerRole {
    CLIENT,
    TRANSPORT_PROXY,
}
