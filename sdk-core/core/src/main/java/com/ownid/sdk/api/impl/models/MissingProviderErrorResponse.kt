package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: No provider is configured for the capability required by the
 * operation, e.g. send SMS for phone-verification.
 *
 * End-user: Not Applicable.
 *
 * Developer action: Configure the missing provider capability for the app and
 * deployment environment.
 */
@Serializable
internal class MissingProviderErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String,

    @SerialName("capability")
    val capability: String,

    @SerialName("scope")
    val scope: ScopeType
)
