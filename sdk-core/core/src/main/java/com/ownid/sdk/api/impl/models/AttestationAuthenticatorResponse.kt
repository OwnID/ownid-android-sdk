package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AttestationAuthenticatorResponse(
    /**
     * Client data in JSON format
     */
    @SerialName("clientDataJSON")
    val clientDataJSON: String,

    /**
     * Attestation object
     */
    @SerialName("attestationObject")
    val attestationObject: String,

    @SerialName("transports")
    val transports: List<TransportType>
)
