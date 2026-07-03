package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AttestationEnrollRequest(
    /**
     * A signed JWT that contains an attested passkey
     */
    @SerialName("proofToken")
    val proofToken: String
)
