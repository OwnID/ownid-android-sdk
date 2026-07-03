package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AttestationOptionsResponse(
    @SerialName("rp")
    val rp: RelayingParty,

    @SerialName("user")
    val user: Fido2User,

    /**
     * The challenge's identifier.
     */
    @SerialName("challenge")
    val challenge: ChallengeId,

    @SerialName("pubKeyCredParams")
    val pubKeyCredParams: List<AttestationOptionsResponsePubKeyCredParamsItem>,

    /**
     * Attestation conveyance during credential generation
     */
    @SerialName("attestation")
    val attestation: AttestationConveyancePreference? = null,

    @SerialName("authenticatorSelection")
    val authenticatorSelection: AttestationOptionsResponseAuthenticatorSelection? = null,

    /**
     * A numerical hint, in milliseconds, which indicates the time the calling web app is willing to
     * wait for the creation operation to complete. This hint may be overridden by the browser.
     */
    @SerialName("timeout")
    val timeout: Timeout? = null,

    @SerialName("excludeCredentials")
    val excludeCredentials: List<PublicKeyCredentialDescriptor>? = null
)
