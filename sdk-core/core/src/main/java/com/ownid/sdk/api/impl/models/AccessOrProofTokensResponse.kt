package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AccessOrProofTokensResponse(
    /**
     * A signed JWT that can be verified as a proof of successful operations
     */
    @SerialName("accessToken")
    val accessToken: String? = null,

    /**
     * A signed JWT that can be verified as a proof of successful single operation
     */
    @SerialName("proofToken")
    val proofToken: String? = null
) {

    init {
        require((accessToken != null) xor (proofToken != null)) { "Exactly one of accessToken or proofToken must be present." }
    }
}
