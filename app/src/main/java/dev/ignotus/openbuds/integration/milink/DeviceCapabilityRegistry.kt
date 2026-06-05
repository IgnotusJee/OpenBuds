package dev.ignotus.openbuds.integration.milink

import dev.ignotus.openbuds.headphones.ConnectedHeadphoneProfile
import dev.ignotus.openbuds.headphones.HeadphoneCapabilities
import dev.ignotus.openbuds.headphones.HeadphoneFeature

/**
 * Static registry of known device capabilities, built from profile templates.
 *
 * This allows querying "what does model X support?" without a live device connection.
 * The registry mirrors the profile objects declared in each adapter and must be kept
 * in sync when new profiles are added.
 *
 * MiLink bridge uses this to enrich snapshot capability flags beyond the 6 already
 * derived from [ConnectedHeadphoneProfile.supports].
 */
object DeviceCapabilityRegistry {
    data class Capabilities(
        val features: Set<HeadphoneFeature>,
        val formFactor: dev.ignotus.openbuds.headphones.HeadphoneFormFactor,
    )

    private val models: Map<String, Capabilities> = mapOf(
        "LinkBuds S" to Capabilities(
            features = setOf(
                HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY,
                HeadphoneFeature.NOISE_CONTROL, HeadphoneFeature.AMBIENT_LEVEL,
                HeadphoneFeature.AMBIENT_VOICE_MODE, HeadphoneFeature.PLAYBACK_CONTROL,
                HeadphoneFeature.VOLUME, HeadphoneFeature.AUDIO_EFFECT,
                HeadphoneFeature.EQ, HeadphoneFeature.CLEAR_BASS,
                HeadphoneFeature.LEA_STATUS, HeadphoneFeature.QUICK_ACCESS,
                HeadphoneFeature.WEARING_STATUS,
            ),
            formFactor = dev.ignotus.openbuds.headphones.HeadphoneFormFactor.TRUE_WIRELESS,
        ),
        "WF-1000XM5" to Capabilities(
            features = setOf(
                HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY,
                HeadphoneFeature.NOISE_CONTROL, HeadphoneFeature.AMBIENT_LEVEL,
                HeadphoneFeature.AMBIENT_VOICE_MODE, HeadphoneFeature.PLAYBACK_CONTROL,
                HeadphoneFeature.VOLUME, HeadphoneFeature.AUDIO_EFFECT,
                HeadphoneFeature.EQ, HeadphoneFeature.CLEAR_BASS,
                HeadphoneFeature.LEA_STATUS, HeadphoneFeature.QUICK_ACCESS,
                HeadphoneFeature.WEARING_STATUS,
            ),
            formFactor = dev.ignotus.openbuds.headphones.HeadphoneFormFactor.TRUE_WIRELESS,
        ),
        "WH-1000XM4" to Capabilities(
            features = setOf(
                HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY,
                HeadphoneFeature.NOISE_CONTROL, HeadphoneFeature.AMBIENT_LEVEL,
                HeadphoneFeature.AMBIENT_VOICE_MODE, HeadphoneFeature.PLAYBACK_CONTROL,
                HeadphoneFeature.VOLUME, HeadphoneFeature.AUDIO_EFFECT,
                HeadphoneFeature.EQ, HeadphoneFeature.CLEAR_BASS,
            ),
            formFactor = dev.ignotus.openbuds.headphones.HeadphoneFormFactor.HEADSET,
        ),
        "C30S" to Capabilities(
            features = setOf(
                HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY,
                HeadphoneFeature.NOISE_CONTROL, HeadphoneFeature.AMBIENT_LEVEL,
                HeadphoneFeature.PLAYBACK_CONTROL, HeadphoneFeature.VOLUME,
                HeadphoneFeature.AUDIO_EFFECT, HeadphoneFeature.EQ,
            ),
            formFactor = dev.ignotus.openbuds.headphones.HeadphoneFormFactor.TRUE_WIRELESS,
        ),
    )

    fun supports(modelName: String, feature: HeadphoneFeature): Boolean =
        models[modelName]?.features?.contains(feature) == true

    fun featuresFor(modelName: String): Set<HeadphoneFeature> =
        models[modelName]?.features.orEmpty()

    fun allModelNames(): Set<String> =
        models.keys
}
