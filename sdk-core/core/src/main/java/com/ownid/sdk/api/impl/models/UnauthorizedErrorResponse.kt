package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The caller is missing valid authentication for the operation.
 *
 * End-user: Prompt the user to authenticate again.
 *
 * Developer action: Refresh credentials, verify token forwarding, and avoid
 * retry loops without new credentials.
 */
@Serializable
internal class UnauthorizedErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
