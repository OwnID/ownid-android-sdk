package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class StartVerificationResponse(
    /**
     * The challenge's identifier
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId,

    @SerialName("resendPolicy")
    val resendPolicy: ResendPolicy,

    /**
     * A numerical hint, in milliseconds, which indicates the time the calling web app is willing to wait for the creation operation to complete. This hint may be overridden by the browser.
     */
    @SerialName("timeout")
    val timeout: Timeout,

    @SerialName("attempts")
    val attempts: Int,

    /**
     * The channel through which the challenge can be completed.
     */
    @SerialName("channel")
    val channel: OperationChannel,

    @SerialName("methods")
    val methods: ChallengeResponseMethods
)
