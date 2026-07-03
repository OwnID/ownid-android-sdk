package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The caller is authenticated but not allowed to perform the operation.
 *
 * End-user: Explain that the action is unavailable or expired.
 *
 * Developer action: Check access token claims or the operation's policy for the
 * required claims.
 */
@Serializable
internal class ForbiddenErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
