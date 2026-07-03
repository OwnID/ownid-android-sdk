package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The response type that will be used to resolve the challenge. Currently only a subset of the types in the protocol are supported
 */
@Serializable
internal enum class StartOidcChallengeRequestOauthResponseType {
    @SerialName("code")
    CODE,

    @SerialName("id_token")
    ID_TOKEN
}
