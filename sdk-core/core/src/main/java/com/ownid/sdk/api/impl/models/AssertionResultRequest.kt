package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AssertionResultRequest(
    /**
     * Base64url encoded credential ID
     */
    @SerialName("id")
    val id: String,

    /**
     * The type of credential, always 'public-key'
     */
    @SerialName("type")
    val type: CredentialType,

    @SerialName("response")
    val response: AssertionAuthenticatorResponse,

    @SerialName("authenticatorAttachment")
    val authenticatorAttachment: AuthenticatorAttachment
)
