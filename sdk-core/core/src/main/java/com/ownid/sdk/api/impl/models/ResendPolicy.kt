package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class ResendPolicy(
    /**
     * Indicates whether resending is allowed.
     */
    @SerialName("allow")
    val allow: Boolean,

    /**
     * The maximum number of resends allowed.
     */
    @SerialName("attempts")
    val attempts: Int,

    /**
     * The delay in seconds before another resend can be made.
     */
    @SerialName("debounce")
    val debounce: Int
)
