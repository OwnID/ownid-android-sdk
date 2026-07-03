package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Client-side error details attached to analytics flow data. */
internal class ClientError(
    /**
     * Client error code.
     */
    @SerialName("errorCode")
    val errorCode: String,

    /**
     * Error source identifier.
     */
    @SerialName("source")
    val source: String? = null,

    /**
     * Human-readable error message.
     */
    @SerialName("message")
    val message: String? = null
)
