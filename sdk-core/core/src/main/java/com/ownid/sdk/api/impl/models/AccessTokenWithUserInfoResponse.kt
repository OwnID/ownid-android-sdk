package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AccessTokenWithUserInfoResponse(
    /**
     * A signed JWT that can be verified as a proof of successful operations
     */
    @SerialName("accessToken")
    val accessToken: String,

    @SerialName("loginId")
    val loginId: LoginId,

    /**
     * The user information
     */
    @SerialName("userInfo")
    val userInfo: Map<String, String>,

    @SerialName("provider")
    val provider: OidcProvider
)
