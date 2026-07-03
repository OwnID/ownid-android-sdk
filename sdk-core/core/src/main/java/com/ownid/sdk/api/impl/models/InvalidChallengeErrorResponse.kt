package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The challenge was not found, expired or already completed.
 *
 * End-user: Ask the user to restart the operation.
 *
 * Developer action: Stop polling or completing this challenge ID and create a
 * fresh challenge.
 */
@Serializable
internal class InvalidChallengeErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String,

    /**
     * The challenge's identifier.
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId
)
