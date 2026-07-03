package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * Request payload for starting a passkey assertion challenge.
 */
internal class AssertionOptionsRequest(
    /**
     * Optional login ID to use when resolving assertion options.
     */
    @SerialName("loginId")
    val loginId: LoginId? = null
)
