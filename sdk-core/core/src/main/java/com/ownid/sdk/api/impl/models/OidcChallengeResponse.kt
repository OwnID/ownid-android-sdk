package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OidcChallengeResponse(
    /**
     * The challenge's identifier.
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId,

    /**
     * A numerical hint, in milliseconds, which indicates the time the calling web app is willing to wait for the creation operation to complete. This hint may be overridden by the browser.
     */
    @SerialName("timeout")
    val timeout: Timeout,

    /**
     * The client ID of the OIDC provider
     */
    @SerialName("clientId")
    val clientId: String,

    /**
     * The URL to navigate to in order to face the challenge, will be provided if using web flow
     */
    @SerialName("challengeUrl")
    val challengeUrl: String? = null
)
