package dev.ignotus.openbuds.lsposed.milink

import dev.ignotus.openbuds.integration.milink.normalizeMac

/** MAC normalization helpers shared by MiLink bridge hooks and cache code. */
object MilinkAirpodsTargetMatcher {
    /**
     * Normalizes a raw MAC string to uppercase colon-separated format.
     * Delegates to the canonical [normalizeMac] extension in the integration package.
     * Returns `null` if the string is blank or doesn't match the expected format.
     */
    fun normalizeMac(value: String?): String? = value?.normalizeMac()
}
