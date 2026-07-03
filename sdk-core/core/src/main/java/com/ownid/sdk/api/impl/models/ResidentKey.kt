package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Resident key requirement values for passkey creation. */
internal enum class ResidentKey {
    @SerialName("required")
    REQUIRED,

    @SerialName("preferred")
    PREFERRED,

    @SerialName("discouraged")
    DISCOURAGED
}
