package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AttestationOptionsRequest(
    @SerialName("loginId")
    val loginId: LoginId? = null,

    /**
     * Display name for the user account
     */
    @SerialName("accountDisplayName")
    val accountDisplayName: String? = null
)
