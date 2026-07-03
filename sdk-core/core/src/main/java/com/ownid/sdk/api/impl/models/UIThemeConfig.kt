package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class UIThemeConfig(
    /**
     * Theme-specific logo URL
     */
    @SerialName("logoUrl")
    val logoUrl: String? = null
)
