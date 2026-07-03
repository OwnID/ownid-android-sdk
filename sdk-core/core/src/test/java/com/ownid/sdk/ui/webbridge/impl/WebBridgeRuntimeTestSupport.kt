@file:Suppress("unused")

package com.ownid.sdk.ui.webbridge.impl

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.ui.webbridge.WebBridgePlugin
import com.ownid.sdk.ui.webbridge.WebBridgePluginKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter

@Implements(WebViewFeature::class)
internal class ShadowOwnIdWebViewFeature {
    internal companion object {
        private val supportedFeatures = mutableSetOf(
            WebViewFeature.WEB_MESSAGE_LISTENER,
            WebViewFeature.DOCUMENT_START_SCRIPT
        )

        @JvmStatic
        @Implementation
        fun isFeatureSupported(feature: String): Boolean = feature in supportedFeatures

        @JvmStatic
        @Resetter
        fun reset() {
            supportedFeatures.clear()
            supportedFeatures += WebViewFeature.WEB_MESSAGE_LISTENER
            supportedFeatures += WebViewFeature.DOCUMENT_START_SCRIPT
        }
    }
}

@Implements(WebViewCompat::class)
internal class ShadowOwnIdWebViewCompat {
    internal data class MessageListenerRegistration(
        val webView: WebView,
        val jsObjectName: String,
        val allowedOriginRules: Set<String>,
        val listener: WebViewCompat.WebMessageListener
    )

    internal data class RemovedMessageListener(
        val webView: WebView,
        val jsObjectName: String
    )

    internal data class DocumentStartScript(
        val webView: WebView,
        val script: String,
        val allowedOriginRules: Set<String>,
        val handler: RecordingScriptHandler
    )

    internal class RecordingScriptHandler : ScriptHandler {
        var removed: Boolean = false
            private set

        override fun remove() {
            removed = true
        }
    }

    internal companion object {
        val messageListeners: MutableList<MessageListenerRegistration> = mutableListOf()
        val removedMessageListeners: MutableList<RemovedMessageListener> = mutableListOf()
        val documentStartScripts: MutableList<DocumentStartScript> = mutableListOf()
        var documentStartFailure: RuntimeException? = null

        @JvmStatic
        @Implementation
        fun addWebMessageListener(
            webView: WebView,
            jsObjectName: String,
            allowedOriginRules: Set<String>,
            listener: WebViewCompat.WebMessageListener
        ) {
            messageListeners += MessageListenerRegistration(webView, jsObjectName, allowedOriginRules.toSet(), listener)
        }

        @JvmStatic
        @Implementation
        fun removeWebMessageListener(webview: WebView, jsObjectName: String) {
            removedMessageListeners += RemovedMessageListener(webview, jsObjectName)
        }

        @JvmStatic
        @Implementation
        fun addDocumentStartJavaScript(webview: WebView, script: String, allowedOriginRules: Set<String>): ScriptHandler {
            documentStartFailure?.let { throw it }
            val handler = RecordingScriptHandler()
            documentStartScripts += DocumentStartScript(webview, script, allowedOriginRules.toSet(), handler)
            return handler
        }

        @JvmStatic
        @Resetter
        fun reset() {
            messageListeners.clear()
            removedMessageListeners.clear()
            documentStartScripts.clear()
            documentStartFailure = null
        }
    }
}

internal object NoopJavaScriptReplyProxy : JavaScriptReplyProxy() {
    override fun postMessage(message: String) = Unit

    override fun postMessage(arrayBuffer: ByteArray) = Unit
}

@OptIn(InternalOwnIdAPI::class)
internal class RecordingRuntimeWebBridgePlugin(
    id: String,
    override val actions: List<String> = listOf("run"),
    private val result: WebBridgePlugin.Result = WebBridgePlugin.Result(Json.parseToJsonElement("""{"handled":true}"""))
) : WebBridgePlugin {
    override val key: WebBridgePluginKey = WebBridgePluginKey.from(id)
    val messages: MutableList<WebBridgePlugin.Message> = mutableListOf()

    override suspend fun handle(message: WebBridgePlugin.Message): WebBridgePlugin.Result {
        messages += message
        return result
    }
}

internal class MutableAppConfigProvider(appConfig: AppConfig = AppConfig.DEFAULT) : AppConfigProvider {
    private val configs = MutableStateFlow(appConfig)

    override suspend fun getOrFetchConfig(): AppConfig = configs.value

    override val config: Flow<AppConfig> = configs
}

internal object WebBridgeRuntimeTestSerialization : Serialization {
    override val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

internal fun appConfigWithAllowedOrigins(allowedOrigins: Set<String>?): AppConfig = AppConfig(
    loginIdConfig = listOf(AppConfig.LoginIdConfig(LoginId.Type.Email)),
    webView = AppConfig.WebViewConfig(allowedOrigins = allowedOrigins),
    logLevel = AppConfig.LogLevel.WARNING
)
