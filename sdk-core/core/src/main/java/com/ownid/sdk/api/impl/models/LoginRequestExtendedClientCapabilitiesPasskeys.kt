package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Passkey-specific capability hints attached to a login request. */
internal class LoginRequestExtendedClientCapabilitiesPasskeys(
    /**
     * Whether the client supports passkey presence checks without starting a full flow.
     */
    @SerialName("peek")
    val peek: Boolean? = null
)
