package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class StartOidcChallengeRequest(
    /**
     * A login ID hint for the OIDC provider
     */
    @SerialName("loginIdHint")
    val loginIdHint: String? = null,

    /**
     * The response type that will be used to resolve the challenge. Currently only a subset of the types in the protocol are supported
     */
    @SerialName("oauthResponseType")
    val oauthResponseType: StartOidcChallengeRequestOauthResponseType,

    /**
     * The redirect URI to be used in an OIDC web flow, defaults to the preconfigured app's redirect uri
     */
    @SerialName("redirectUri")
    val redirectUri: String? = null
)
