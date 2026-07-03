package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Server-provided logging levels for the SDK configuration. */
internal enum class LogLevel {
    @SerialName("Error")
    ERROR,

    @SerialName("Warning")
    WARNING,

    @SerialName("Information")
    INFORMATION,

    @SerialName("Debug")
    DEBUG,

    @SerialName("None")
    NONE
}
