package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class PasskeyCancelRequest(
    /**
     * The challenge's identifier.
     */
    @SerialName("challenge")
    val challenge: ChallengeId,

    /**
     * The reason for canceling the operation
     */
    @SerialName("reason")
    val reason: CancelReason
)
