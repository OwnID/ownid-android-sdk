package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The verification code resend limit or debounce policy was reached.
 *
 * End-user: Ask the user to wait or restart the challenge.
 *
 * Developer action: Disable resend UI for the current challenge and respect the
 * server resend policy.
 */
@Serializable
internal class MaximumResendAttemptsReachedErrorResponse(
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
