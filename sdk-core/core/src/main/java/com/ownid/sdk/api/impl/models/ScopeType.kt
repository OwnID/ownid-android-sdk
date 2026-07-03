package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class ScopeType {
    @SerialName("data")
    DATA,

    @SerialName("channel")
    CHANNEL,

    @SerialName("session")
    SESSION
}
