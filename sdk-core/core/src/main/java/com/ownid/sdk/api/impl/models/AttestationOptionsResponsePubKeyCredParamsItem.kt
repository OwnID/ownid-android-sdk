package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AttestationOptionsResponsePubKeyCredParamsItem(
    /**
     * The type of credential, always 'public-key'
     */
    @SerialName("type")
    val type: CredentialType,

    /**
     * The cryptographic algorithm to be used in COSE format
     */
    @SerialName("alg")
    val alg: KeyAlgorithmType
)
