package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The login ID value does not match the validation rule configured for
 * its type.
 *
 * End-user: Ask the user to correct the identifier.
 *
 * Developer action: Surface the configured regex only in diagnostics and keep
 * client validation aligned with server config.
 */
@Serializable
internal class LoginIdValidationErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String,

    @SerialName("loginId")
    val loginId: LoginId,

    @SerialName("regex")
    val regex: String
)
