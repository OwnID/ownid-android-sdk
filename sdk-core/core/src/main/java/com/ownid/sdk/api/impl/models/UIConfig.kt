package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UI configuration with theme-specific assets
 */
@Serializable
internal class UIConfig(
    @SerialName("default")
    val default: UIThemeConfig,

    @SerialName("dark")
    val dark: UIThemeConfig? = null
)
