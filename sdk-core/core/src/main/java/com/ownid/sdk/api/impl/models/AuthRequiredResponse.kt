package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AuthRequiredResponse(
    /**
     * The reason why the session was not created
     */
    @SerialName("reason")
    val reason: String? = null,

    /**
     * Hint if the account was not found. Will be provided in a discovery call only for apps that enable this exposure.
     */
    @SerialName("accountNotFound")
    val accountNotFound: Boolean? = null,

    /**
     * Hint if the account is blocked. Will be provided in a discovery call only for apps that enable this exposure.
     */
    @SerialName("accountBlocked")
    val accountBlocked: Boolean? = null,

    /**
     * Required operations needed to complete authentication.
     */
    @SerialName("authRequirements")
    val authRequirements: AuthRequirements? = null
) {

    init {
        require(
            listOf(
                accountNotFound,
                accountBlocked,
                authRequirements
            ).count { it != null } == 1
        ) { "Exactly one of accountNotFound, accountBlocked, or authRequirements must be present." }
    }
}
