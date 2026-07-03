package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** User verification policy values for passkey ceremonies. */
internal enum class UserVerification {
    @SerialName("preferred")
    PREFERRED,

    @SerialName("required")
    REQUIRED,

    @SerialName("discouraged")
    DISCOURAGED
}
