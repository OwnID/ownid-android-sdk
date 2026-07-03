package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Attestation conveyance during credential generation
 */
@Serializable
internal enum class AttestationConveyancePreference {
    @SerialName("none")
    NONE,

    @SerialName("direct")
    DIRECT,

    @SerialName("indirect")
    INDIRECT,

    @SerialName("enterprise")
    ENTERPRISE
}
