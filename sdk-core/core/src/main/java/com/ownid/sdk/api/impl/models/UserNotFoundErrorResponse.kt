package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The account provider did not find an account for the requested login
 * ID.
 *
 * End-user: Direct the user to register an account.
 *
 * Developer action: Treat this as an expected business outcome; do not escalate
 * unless provider data is inconsistent.
 */
@Serializable
internal class UserNotFoundErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
