package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Extended client capability hints attached to a login request. */
internal class LoginRequestExtendedClientCapabilities(
    /**
     * Passkey capability hints.
     */
    @SerialName("passkeys")
    val passkeys: LoginRequestExtendedClientCapabilitiesPasskeys? = null
)
