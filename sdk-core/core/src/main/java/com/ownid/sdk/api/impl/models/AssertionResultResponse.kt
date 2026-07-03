package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AssertionResultResponse(
    /**
     * A signed JWT that can be verified as a proof of successful operations
     */
    @SerialName("accessToken")
    val accessToken: String
)
