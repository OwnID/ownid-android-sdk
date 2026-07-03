package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AssertionOptionsResponse(
    /**
     * The challenge's identifier.
     */
    @SerialName("challenge")
    val challenge: ChallengeId,

    /**
     * Relying Party identifier
     */
    @SerialName("rpId")
    val rpId: String,

    @SerialName("allowCredentials")
    val allowCredentials: List<PublicKeyCredentialDescriptor>? = null,

    @SerialName("userVerification")
    val userVerification: UserVerification? = null,

    /**
     * A numerical hint, in milliseconds, which indicates the time the calling web app is willing to
     * wait for the creation operation to complete. This hint may be overridden by the browser.
     */
    @SerialName("timeout")
    val timeout: Timeout? = null
)
