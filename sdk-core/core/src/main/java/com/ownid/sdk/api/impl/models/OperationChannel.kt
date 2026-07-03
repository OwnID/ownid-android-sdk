package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OperationChannel(
    /**
     * The channel through which the operation can be performed. The channel will be masked if it was
     * not provided as operation input.
     */
    @SerialName("channel")
    val channel: String,

    @SerialName("id")
    val id: String
)
