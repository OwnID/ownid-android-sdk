package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Authentication method identifiers used in API and analytics payloads. */
internal enum class AuthMethod {
    @SerialName("otp")
    OTP,

    @SerialName("passkey")
    PASSKEY,

    @SerialName("magic-link")
    MAGIC_LINK,

    @SerialName("password")
    PASSWORD,

    @SerialName("deferred")
    DEFERRED,

    @SerialName("immediate")
    IMMEDIATE,

    @SerialName("unknown")
    UNKNOWN,

    @SerialName("social-google")
    SOCIAL_GOOGLE,

    @SerialName("social-apple")
    SOCIAL_APPLE,

    @SerialName("facekey")
    FACEKEY
}
