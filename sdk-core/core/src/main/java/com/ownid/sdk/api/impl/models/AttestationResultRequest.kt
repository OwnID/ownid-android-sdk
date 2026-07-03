package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AttestationResultRequest(
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
    val response: AttestationAuthenticatorResponse,

    @SerialName("authenticatorAttachment")
    val authenticatorAttachment: AuthenticatorAttachment? = null
)
