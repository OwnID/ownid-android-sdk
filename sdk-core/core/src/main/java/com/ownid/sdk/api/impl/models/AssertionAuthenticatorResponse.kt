package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AssertionAuthenticatorResponse(
    /**
     * Client data in JSON format
     */
    @SerialName("clientDataJSON")
    val clientDataJSON: String,

    /**
     * Authenticator data used in the assertion signing process
     */
    @SerialName("authenticatorData")
    val authenticatorData: String,

    /**
     * Base64url encoded identifier for a user account, specified by the Relying Party as user.id during registration
     */
    @SerialName("userHandle")
    val userHandle: String? = null,

    /**
     * Signature for the server challenge, returned from the authenticator
     */
    @SerialName("signature")
    val signature: String
)
