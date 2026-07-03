package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The user exhausted the allowed verification attempts for the
 * challenge.
 *
 * End-user: Ask the user to start a new challenge.
 *
 * Developer action: Do not retry the same challenge; clear local challenge
 * state and restart the flow.
 */
@Serializable
internal class MaximumAttemptsReachedErrorResponse(
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
