package com.ownid.sdk.model

import kotlinx.serialization.Serializable

/**
 * Internal runtime configuration for one OwnID app/environment.
 *
 * Values come from `/api/config/app`, from the stored configuration file, or from [AppConfig.DEFAULT] when no remote/stored
 * configuration can be applied during bootstrap. The model is shared by SDK capabilities for login ID validation,
 * WebView defaults and origin rules, and server-log filtering. It also carries runtime UI asset data.
 *
 * This model is not the public app configuration API.
 */
@Serializable
internal class AppConfig(
    internal val loginIdConfig: List<LoginIdConfig>,
    internal val displayName: String? = null,
    internal val webView: WebViewConfig? = null,
    internal val ui: UIConfig? = null,
    internal val logLevel: LogLevel,
) {
    internal companion object {
        /** Built-in fallback used when neither remote nor stored configuration is available. */
        internal val DEFAULT: AppConfig = AppConfig(
            loginIdConfig = LoginIdConfiguration.DEFAULT_EMAIL.supportedTypes.map { LoginIdConfig(type = it) },
            logLevel = LogLevel.WARNING
        )
    }

    override fun equals(other: Any?): Boolean =
        other is AppConfig && loginIdConfig == other.loginIdConfig && displayName == other.displayName && webView == other.webView && ui == other.ui && logLevel == other.logLevel

    override fun hashCode(): Int =
        (((loginIdConfig.hashCode() * 31 + displayName.hashCode()) * 31 + webView.hashCode()) * 31 + ui.hashCode()) * 31 + logLevel.hashCode()

    @Serializable
    internal class LoginIdConfig(
        internal val type: LoginId.Type,
        internal val regex: String? = null,
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is LoginIdConfig && type == other.type && regex == other.regex)

        override fun hashCode(): Int = 31 * type.hashCode() + regex.hashCode()
    }

    @Serializable
    internal class WebViewConfig(
        internal val baseUrl: String? = null,
        internal val html: String? = null,
        internal val allowedOrigins: Set<String>? = null,
    ) {
        override fun equals(other: Any?): Boolean =
            other is WebViewConfig && baseUrl == other.baseUrl && html == other.html && allowedOrigins == other.allowedOrigins

        override fun hashCode(): Int = (baseUrl.hashCode() * 31 + html.hashCode()) * 31 + allowedOrigins.hashCode()
    }

    @Serializable
    internal class UIConfig(
        internal val default: UIThemeConfig,
        internal val dark: UIThemeConfig? = null,
    ) {
        override fun equals(other: Any?): Boolean = this === other || (other is UIConfig && default == other.default && dark == other.dark)

        override fun hashCode(): Int = 31 * default.hashCode() + dark.hashCode()

        @Serializable
        internal class UIThemeConfig(
            internal val logoUrl: String? = null
        ) {
            override fun equals(other: Any?): Boolean = this === other || (other is UIThemeConfig && logoUrl == other.logoUrl)

            override fun hashCode(): Int = logoUrl.hashCode()
        }
    }

    @Serializable
    internal enum class LogLevel { ERROR, WARNING, INFORMATION, DEBUG, NONE }
}
