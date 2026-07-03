package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class UserInfo(
    @SerialName("loginId")
    val loginId: LoginId,

    @SerialName("returningUser")
    val returningUser: Boolean? = null,

    /**
     * The last authentication method used by the user, if is a returning user
     */
    @SerialName("lastAuthMethod")
    val lastAuthMethod: AuthMethod? = null
)
