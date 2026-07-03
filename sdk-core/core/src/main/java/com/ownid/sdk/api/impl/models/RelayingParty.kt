package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class RelayingParty(
    /**
     * Relying Party identifier (passkey's domain)
     */
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String
)
