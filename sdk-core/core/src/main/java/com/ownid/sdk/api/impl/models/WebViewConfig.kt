package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class WebViewConfig(
    /**
     * Base URL for the webview
     */
    @SerialName("baseUrl")
    val baseUrl: String? = null,

    /**
     * HTML content for the webview
     */
    @SerialName("html")
    val html: String? = null,

    /**
     * Allowed webview origins
     */
    @SerialName("allowedOrigins")
    val allowedOrigins: List<String>? = null
)
