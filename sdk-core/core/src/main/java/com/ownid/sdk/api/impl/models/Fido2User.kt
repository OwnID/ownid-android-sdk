package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class Fido2User(
    /**
     * Base64url encoded user ID
     */
    @SerialName("id")
    val id: String,

    /**
     * User's name
     */
    @SerialName("name")
    val name: String,

    /**
     * User's display name
     */
    @SerialName("displayName")
    val displayName: String
)
