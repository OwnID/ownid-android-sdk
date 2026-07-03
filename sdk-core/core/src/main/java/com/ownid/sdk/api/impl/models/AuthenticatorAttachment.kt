package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Authenticator attachment preference for passkey ceremonies. */
internal enum class AuthenticatorAttachment {
    @SerialName("platform")
    PLATFORM,

    @SerialName("cross-platform")
    CROSS_PLATFORM
}
