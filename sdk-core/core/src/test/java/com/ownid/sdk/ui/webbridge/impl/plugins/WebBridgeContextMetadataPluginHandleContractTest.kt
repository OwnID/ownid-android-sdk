package com.ownid.sdk.ui.webbridge.impl.plugins

import android.net.Uri
import android.webkit.WebView
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.model.Authz
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
// Covers: WB-BRIDGE-210
internal class WebBridgeContextMetadataPluginHandleContractTest {

    @Test
    fun contextGetReturnsScopedLoginIdAndDisplayNamePayload(): Unit = runTest {
        val context = com.ownid.sdk.model.Context.Builder().apply {
            authz = Authz.start(LoginId("person@example.test", LoginId.Type.Email))
            accountDisplayName = "Person Example"
        }.build(scopeName = "WebBridgeContextMetadataPluginHandleContractTest")
        val plugin = WebBridgeContextPluginImpl(context = context)

        val result = plugin.handle(message(pluginId = "context", action = "get"))

        val success = result.success!!.jsonObject
        assertThat(success["authz"]!!.jsonObject["loginId"]!!.jsonPrimitive.content).isEqualTo("person@example.test")
        assertThat(success["accountDisplayName"]!!.jsonPrimitive.content).isEqualTo("Person Example")
        assertThat(result.error).isNull()
    }

    @Test
    fun contextGetReturnsScopedAccessTokenPayload(): Unit = runTest {
        val context = com.ownid.sdk.model.Context.Builder().apply {
            authz = Authz.fromToken("access-token")
        }.build(scopeName = "WebBridgeContextMetadataPluginHandleContractTest")
        val plugin = WebBridgeContextPluginImpl(context = context)

        val result = plugin.handle(message(pluginId = "CONTEXT", action = "GET"))

        val success = result.success!!.jsonObject
        assertThat(success["authz"]!!.jsonObject["accessToken"]!!.jsonPrimitive.content).isEqualTo("access-token")
        assertThat(success["accountDisplayName"]).isNull()
        assertThat(result.error).isNull()
    }

    @Test
    fun contextGetReturnsUnavailableErrorWhenScopedContextIsMissing(): Unit = runTest {
        val plugin = WebBridgeContextPluginImpl(context = null)

        val result = plugin.handle(message(pluginId = "CONTEXT", action = "get"))

        assertThat(result.success).isNull()
        assertThat(result.error!!["message"]!!.jsonPrimitive.contentOrNull).isEqualTo("Context is unavailable")
    }

    @Test
    fun metadataGetReturnsLocalCorrelationIdPayload(): Unit = runTest {
        val plugin = WebBridgeMetadataPluginImpl(localInfo = TestLocalInfo)

        val result = plugin.handle(message(pluginId = "metadata", action = "get"))

        assertThat(result.success!!.jsonObject["correlationId"]!!.jsonPrimitive.content).isEqualTo("correlation-id")
        assertThat(result.error).isNull()
    }

    @Test
    fun metadataUnknownActionReturnsBridgeError(): Unit = runTest {
        val plugin = WebBridgeMetadataPluginImpl(localInfo = TestLocalInfo)

        val result = plugin.handle(message(pluginId = "METADATA", action = "missing"))

        assertThat(result.success).isNull()
        assertThat(result.error!!["message"]!!.jsonPrimitive.contentOrNull).isEqualTo("Unknown action: missing")
    }

    private fun message(pluginId: String, action: String, params: String? = null): WebBridgePlugin.Message =
        WebBridgePlugin.Message(
            webView = WebView(RuntimeEnvironment.getApplication()),
            sourceOrigin = Uri.parse("https://example.test"),
            isMainFrame = true,
            allowedOriginRules = setOf("https://example.test"),
            payload = WebBridgePlugin.Message.Payload(
                pluginId = pluginId,
                action = action,
                callbackPath = "ownid.callback",
                params = params
            )
        )

    private object TestLocalInfo : LocalInfo {
        override val modules: List<Pair<String, String>> = listOf("core" to "test")
        override val packageName: String = "com.ownid.webbridge.test"
        override val appVersion: String = "1"
        override val userAgent: String = "OwnID WebBridge Test"
        override val correlationId: String = "correlation-id"
        override val isDebuggable: Boolean = true
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean = false
        override val isFaceHardwarePresent: Boolean = false
        override val isIrisHardwarePresent: Boolean = false
        override val isStrongBiometricEnabled: Boolean = false
    }
}
