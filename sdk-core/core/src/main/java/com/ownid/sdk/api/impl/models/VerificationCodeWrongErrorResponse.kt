package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The submitted verification code or equivalent challenge proof is
 * incorrect.
 *
 * End-user: Ask the user to try again while attempts remain.
 *
 * Developer action: Keep the current challenge active and update
 * remaining-attempt UI if available.
 */
@Serializable
internal class VerificationCodeWrongErrorResponse(
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
