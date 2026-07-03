package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * Authenticator selection criteria returned for passkey attestation.
 */
internal class AttestationOptionsResponseAuthenticatorSelection(
    /**
     * Preferred authenticator attachment.
     */
    @SerialName("authenticatorAttachment")
    val authenticatorAttachment: AuthenticatorAttachment? = null,

    /**
     * Required user verification policy.
     */
    @SerialName("userVerification")
    val userVerification: UserVerification? = null,

    /**
     * Resident key requirement for the created credential.
     */
    @SerialName("residentKey")
    val residentKey: ResidentKey? = null
)
