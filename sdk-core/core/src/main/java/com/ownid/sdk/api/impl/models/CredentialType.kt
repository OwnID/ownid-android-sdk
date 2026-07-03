package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of credential, always 'public-key'
 */
@Serializable
internal enum class CredentialType {
    @SerialName("public-key")
    PUBLIC_KEY
}
