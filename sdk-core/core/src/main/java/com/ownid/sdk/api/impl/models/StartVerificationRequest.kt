package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class StartVerificationRequest(
    @SerialName("loginId")
    val loginId: LoginId? = null,

    /**
     * An optional hint identifier for the login ID
     */
    @SerialName("loginIdHintId")
    val loginIdHintId: String? = null,

    @SerialName("verificationMethods")
    val verificationMethods: List<VerificationMethod>? = null,

    /**
     * An optional URL to which the user will be redirected to after clicking a magic link, if this
     * method is used in the verification
     */
    @SerialName("magicLinkRedirectUrl")
    val magicLinkRedirectUrl: String? = null
)
