package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: A configured provider failed while OwnID was processing the operation,
 * e.g. integration-endpoint failed to fetch the user, email-server failed to
 * send the email.
 *
 * End-user: Show a temporary failure message.
 *
 * Developer action: Log provider request context, monitor the provider
 * integration, and escalate repeated failures.
 */
@Serializable
internal class ProviderErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String,

    @SerialName("scope")
    val scope: ScopeType
)
