package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * Verification methods available for a started challenge.
 *
 * At least one of [otp] or [magicLink] must be present.
 */
internal class ChallengeResponseMethods(
    /**
     * OTP verification configuration.
     */
    @SerialName("otp")
    val otp: ChallengeResponseMethodsOtp? = null,

    /**
     * Magic-link verification availability marker.
     */
    @SerialName("magicLink")
    val magicLink: ChallengeResponseMethodsMagicLink? = null
) {

    init {
        require(otp != null || magicLink != null) { "At least one of otp or magicLink must be present." }
    }
}
