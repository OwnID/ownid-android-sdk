package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for canceling a challenge operation.
 */
@Serializable
internal class CancelVerificationRequest(
    /**
     * The challenge's identifier.
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId,

    @SerialName("reason")
    val reason: CancelReason? = null
)
