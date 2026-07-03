package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Application auth configuration returned by the server.
 *
 * Capabilities from the contract are intentionally ignored as unsupported.
 */
@Serializable
internal class AppAuthConfig(
    /**
     * Application display name
     */
    @SerialName("displayName")
    val displayName: String? = null,

    /**
     * Settings related to login ID types
     */
    @SerialName("loginIdConfig")
    val loginIdConfig: List<AppAuthConfigLoginIdConfigItem>,

    /**
     * WebView configuration delivered with the auth config.
     */
    @SerialName("webView")
    val webView: WebViewConfig? = null,

    /**
     * UI configuration with theme-specific assets
     */
    @SerialName("ui")
    val ui: UIConfig? = null,

    /**
     * Server-provided logging level.
     */
    @SerialName("logLevel")
    val logLevel: LogLevel? = null
)
