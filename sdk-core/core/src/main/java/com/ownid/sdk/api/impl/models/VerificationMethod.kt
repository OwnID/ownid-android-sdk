package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Verification delivery methods supported by a challenge. */
internal enum class VerificationMethod {
    @SerialName("MagicLink")
    MAGIC_LINK,

    @SerialName("Otp")
    OTP
}
