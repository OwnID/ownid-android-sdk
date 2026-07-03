package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Client device capabilities reported with analytics events. */
internal class ClientDeviceInfo(
    /**
     * Whether a platform authenticator is available on the device.
     */
    @SerialName("isPlatformAuthenticatorAvailable")
    val isPlatformAuthenticatorAvailable: Boolean,

    /**
     * Whether the client is running inside a WebView.
     */
    @SerialName("isWebView")
    val isWebView: Boolean,

    /**
     * Whether the client is a mobile-native SDK integration.
     */
    @SerialName("isMobileNative")
    val isMobileNative: Boolean
)
