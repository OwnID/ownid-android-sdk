package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class VerificationRequest(
    /**
     * The challenge's identifier.
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId
)
