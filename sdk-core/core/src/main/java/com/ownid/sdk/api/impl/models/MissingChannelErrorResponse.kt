package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The selected verification channel is unavailable for the login ID.
 *
 * End-user: Offer another authentication method or ask the user to update
 * account contact details.
 *
 * Developer action: Check account provider data and channel configuration
 * before retrying.
 */
@Serializable
internal class MissingChannelErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String,

    @SerialName("loginId")
    val loginId: LoginId
)
