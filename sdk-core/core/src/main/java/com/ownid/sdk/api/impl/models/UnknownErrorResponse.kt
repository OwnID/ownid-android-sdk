package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The server could not map the failure to a more specific public error
 * code.
 *
 * End-user: Show a generic failure message and suggest retrying.
 *
 * Developer action: Log the correlation context, inspect server logs, and
 * escalate if the issue repeats.
 */
@Serializable
internal class UnknownErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
