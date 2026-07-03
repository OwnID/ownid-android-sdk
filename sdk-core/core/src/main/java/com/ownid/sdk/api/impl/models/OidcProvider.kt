package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** OIDC providers supported by the auth API. */
internal enum class OidcProvider {
    @SerialName("Google")
    GOOGLE,

    @SerialName("Apple")
    APPLE
}
