package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Request payload for starting login/authentication. */
internal class LoginRequest(
    /**
     * Optional login ID to authenticate.
     */
    @SerialName("loginId")
    val loginId: LoginId? = null,

    /**
     * Optional extended client capability hints.
     */
    @SerialName("extendedClientCapabilities")
    val extendedClientCapabilities: LoginRequestExtendedClientCapabilities? = null
)
