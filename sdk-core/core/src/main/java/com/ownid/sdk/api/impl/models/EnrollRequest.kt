package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class EnrollRequest(
    /**
     * A signed JWT that contains a verified channel
     */
    @SerialName("proofToken")
    val proofToken: String
)
