package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The request payload, route parameter, query parameter, or operation
 * state is invalid.
 *
 * End-user: Not Applicable
 *
 * Developer action: Validate the request has all the required fields in their
 * expected format.
 */
@Serializable
internal class InvalidArgumentErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
