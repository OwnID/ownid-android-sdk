package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * Request payload for canceling an OIDC challenge.
 */
internal class CancelOidcChallengeRequest(
    /**
     * The challenge's identifier.
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId,

    /**
     * Optional cancellation reason.
     */
    @SerialName("reason")
    val reason: CancelReason? = null
)
