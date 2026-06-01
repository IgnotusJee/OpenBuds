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

    fun verify(uid: Int) {
        if (!isAllowedUid(uid)) {
            throw SecurityException("MiLink bridge caller uid=$uid is not allowed")
        }
    }

    fun isAllowedUid(uid: Int): Boolean {
        if (uid == ownUid()) return true
        val packages = packageResolver(uid).toSet()
        return MilinkBridgeContract.MILINK_PACKAGE in packages ||
            MilinkBridgeContract.OPENBUDS_PACKAGE in packages
    }
}
