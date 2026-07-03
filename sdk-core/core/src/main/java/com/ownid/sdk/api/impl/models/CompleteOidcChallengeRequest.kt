package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class CompleteOidcChallengeRequest(
    /**
     * The challenge's identifier.
     */
    @SerialName("challengeId")
    val challengeId: ChallengeId,

    /**
     * The authorization code returned by the OIDC provider
     */
    @SerialName("code")
    val code: String? = null,

    /**
     * The ID token returned by the OIDC provider
     */
    @SerialName("idToken")
    val idToken: String? = null
) {

    init {
        require((code != null) xor (idToken != null)) { "Exactly one of code or idToken must be present." }
    }
}
