package com.ownid.sdk.internal.component.events

import android.os.Build
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.view.OwnIdButton
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class Metadata(
    private val applicationName: String? = null,
    private val correlationId: String? = null,
    private val widgetPosition: OwnIdButton.Position? = null,
    private val widgetType: WidgetType? = null,
    private val widgetId: String? = null, // WebSDK field
    private val webViewOrigin: String? = null,
    private val loginType: OwnIdLoginType? = null,
    private val authType: String? = null, // Proxy server value via OwnIdFlowInfo.authType
    private val hasLoginId: Boolean? = null,
    private val validLoginIdFormat: Boolean? = null,
    private val stackTrace: String? = null,
    private val returningUser: Boolean? = null,
    private val isUserVerifyingPlatformAuthenticatorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
) {

    @InternalOwnIdAPI
    public enum class WidgetType(internal val value: String) {
        FINGERPRINT("button-fingerprint"),
        CUSTOM("client-button"),
        AUTH_BUTTON("ownid-auth-button"),
        PROMPT("prompt");
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    @Throws(OwnIdException::class)
    public fun toJSONObject(): JSONObject = runCatching {
        JSONObject().apply {
            if (applicationName != null) put("applicationName", applicationName)
            if (correlationId != null) put("correlationId", correlationId)
            put("isUserVerifyingPlatformAuthenticatorAvailable", isUserVerifyingPlatformAuthenticatorAvailable)
            if (widgetPosition != null) put("widgetPosition", widgetPosition.name.lowercase())
            if (widgetType != null) put("widgetType", widgetType.value)
            if (widgetId != null) put("widgetId", widgetId)
            if (webViewOrigin != null) put("webViewOrigin", webViewOrigin)
            if (loginType != null) put("loginType", loginType.name.replaceFirstChar { it.lowercase() })
            if (authType != null) put("authType", authType)
            if (hasLoginId != null) put("hasLoginId", hasLoginId)
            if (validLoginIdFormat != null) put("validLoginIdFormat", validLoginIdFormat)
//            if (returningUser != null) put("returningUser", returningUser)
            if (stackTrace != null) put("stackTrace", stackTrace)
        }
    }.getOrElse {
        throw OwnIdException("Metadata.toJsonString", it)
    }
}