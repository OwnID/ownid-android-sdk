package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class PublicKeyCredentialDescriptor(
    /**
     * The type of credential, always 'public-key'
     */
    @SerialName("type")
    val type: CredentialType,

    /**
     * Base64url encoded credential ID
     */
    @SerialName("id")
    val id: String,

    @SerialName("transports")
    val transports: List<TransportType>? = null
)
