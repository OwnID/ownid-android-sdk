package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The supplied login ID type is not supported by the app or operation.
 *
 * End-user: Ask for a supported identifier such as email or phone, based on app
 * configuration.
 *
 * Developer action: Compare the requested login ID type with the app's login ID
 * configuration.
 */
@Serializable
internal class LoginIdTypeNotSupportedErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
