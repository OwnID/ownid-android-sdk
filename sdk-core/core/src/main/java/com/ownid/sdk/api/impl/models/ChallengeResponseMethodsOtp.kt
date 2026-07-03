package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** OTP verification configuration for a challenge. */
internal class ChallengeResponseMethodsOtp(
    /**
     * Expected OTP code length.
     */
    @SerialName("length")
    val length: Int? = null
)
