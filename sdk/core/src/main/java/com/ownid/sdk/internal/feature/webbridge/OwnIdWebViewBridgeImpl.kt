package com.ownid.sdk.internal.feature.webbridge

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Looper
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeFido
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeMetadata
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.internal.toHostHeader
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdWebViewBridgeImpl(
    private val instanceName: InstanceName,
    includeNamespaces: List<OwnIdWebViewBridge.Namespace>?,
    excludeNamespaces: List<OwnIdWebViewBridge.Namespace>?
) : OwnIdWebViewBridge {

    @InternalOwnIdAPI
    internal interface NamespaceHandler {
        val namespace: OwnIdWebViewBridge.Namespace
        val actions: Array<String>

        @UiThread
        fun handle(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?)
    }

    @InternalOwnIdAPI
    internal interface BridgeCallback<R : Any?, E : Any> {
        fun onResult(result: R)
        fun onError(error: E)
    }

    private val supportedNamespacesMap = mapOf(
        OwnIdWebViewBridge.Namespace.FIDO to OwnIdWebViewBridgeFido,
        OwnIdWebViewBridge.Namespace.STORAGE to OwnIdWebViewBridgeStorage,
        OwnIdWebViewBridge.Namespace.METADATA to OwnIdWebViewBridgeMetadata
    )

    private val namespaceHandlers: List<NamespaceHandler>

    init {
        val exclude = excludeNamespaces ?: emptyList()
        namespaceHandlers = (includeNamespaces ?: supportedNamespacesMap.keys)
            .filterNot { exclude.contains(it) }
            .mapNotNull { supportedNamespacesMap[it] }
    }

    private val features =
        JSONObject().apply { namespaceHandlers.forEach { put(it.namespace.name, JSONArray(it.actions)) } }.toString()

    @Volatile
    private var bridgeJob: Job? = null

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var allowedOriginRules: List<String> = emptyList()

    @Volatile
    private var callbackMap: Map<NamespaceHandler, BridgeCallback<*, *>> = emptyMap()

    @MainThread
    public override fun injectInto(
        webView: WebView,
        allowedOriginRules: Set<String>,
        owner: LifecycleOwner?,
        synchronous: Boolean,
        onResult: OwnIdWebViewBridge.InjectCallback?
    ) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        require(owner != null) { "WebView lifecycle owner must be set" }

        owner.lifecycle.coroutineScope.launch {
            try {
                injectInto(webView, allowedOriginRules, synchronous)
                onResult?.onResult(null)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                onResult?.onResult(OwnIdException.map("Injection failed: ${cause.message}", cause))
            }
        }
    }

    private val ownIdNativeBridgeJS = """
window.__ownidNativeBridge = {
  getNamespaces: function getNamespaces() { return '""" + features + """'; },
  invokeNative: function invokeNative(namespace, action, callbackPath, params, metadata) {
    try {
      window.__ownidNativeBridgeHandler.postMessage(JSON.stringify({ namespace, action, callbackPath, params, metadata }));
    } catch (error) {
      setTimeout(function () {
        eval(callbackPath + '(false);');
      });
    }
  }
};
"""

    private val webMessageListener = object : WebViewCompat.WebMessageListener {
        @UiThread
        override fun onPostMessage(
            view: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, replyProxy: JavaScriptReplyProxy
        ) {
            runCatching {
                val data = JSONObject(requireNotNull(message.data))
                val metadataJSON = JSONObject(data.getString("metadata"))
                val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl
                ownIdCore.eventsService.sendMetric(
                    category = Metric.Category.fromStringOrDefault(metadataJSON.optString("category")),
                    Metric.EventType.Track,
                    action = "WebViewBridge: received command [${data.optString("namespace")}:${data.optString("action")}]",
                    context = metadataJSON.optString("context"),
                    metadata = Metadata(webViewOrigin = sourceOrigin.toString(), widgetId = metadataJSON.optString("widgetId")),
                    siteUrl = metadataJSON.optString("siteUrl")
                )
            }.onFailure {
                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeImpl, "onPostMessage", it.message)
            }

            val bridgeJob = this@OwnIdWebViewBridgeImpl.bridgeJob
            if (bridgeJob == null || bridgeJob.isCompleted) {
                OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "onPostMessage", "Operation canceled by caller")
                return
            }

            val webView = this@OwnIdWebViewBridgeImpl.webView
            if (webView == null) {
                OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "onPostMessage", "WebView is unavailable")
                return
            }

            try {
                val data = JSONObject(requireNotNull(message.data) { "Parameter required: 'message.data'" })
                val callbackPath =
                    requireNotNull(data.optString("callbackPath").ifBlank { null }) { "Parameter required: 'callbackPath'" }
                val namespace = data.optString("namespace")
                val action = data.optString("action")
                val params = data.optString("params").ifBlank { null }

                val msg = "[$namespace:$action] sourceOrigin: $sourceOrigin, isMainFrame: $isMainFrame"
                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeImpl, "onPostMessage", msg)

                namespaceHandlers.firstOrNull { it.namespace.name.equals(namespace, ignoreCase = true) }?.run {
                    val context = OwnIdWebViewBridgeContext(
                        OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl,
                        webView,
                        bridgeJob,
                        allowedOriginRules,
                        callbackMap[this],
                        sourceOrigin,
                        isMainFrame,
                        callbackPath
                    )

                    handle(context, action, params)

                } ?: OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeImpl, "onPostMessage", "No namespace found: '$namespace'")
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeImpl, "onPostMessage", cause.message, cause)
            }
        }
    }

    /**
     * Injects the OwnID WebView Bridge into the specified [WebView].
     *
     * **Coroutine Context:** This function must be called within a coroutine context that is bound to the WebView's lifecycle.
     *
     * **Allowed Origin Rules:**
     * * **Synchronous Injection (`synchronous = true`):**
     *     * If OwnID application configuration is available, allowed origins are derived from the server configuration plus any values provided in the `allowedOriginRules` parameter.
     *     * If OwnID application configuration is unavailable and `allowedOriginRules` is not empty, only values from `allowedOriginRules` are used.
     *     * If both OwnID application configuration and `allowedOriginRules` are unavailable or empty, a wildcard rule (`*`) is used, allowing communication from any origin. **This is less secure and should be avoided if possible.**
     *
     * * **Asynchronous Injection (`synchronous = false`):**
     *     * **Always waits** for the OwnID application configuration to become available before proceeding with injection.
     *     * Allowed origins are derived from the server configuration plus any values provided in the `allowedOriginRules` parameter.
     *     * Injection will **fail** if the OwnID application configuration cannot be fetched.
     *
     * **Error Handling:**
     * This function throws `OwnIdException` if any of the following conditions occur:
     * * Failure to fetch the OwnID application configuration (in asynchronous mode).
     * * No valid HTTPS URLs are provided in the OwnID application configuration or `allowedOriginRules` (in asynchronous mode).
     * * The WebView doesn't support `WebViewFeature.WEB_MESSAGE_LISTENER` or `WebViewFeature.DOCUMENT_START_SCRIPT`.
     *
     * @param webView The WebView instance to inject the bridge into.
     * @param allowedOriginRules A set of allowed origin rules to be used in addition to those from the OwnID application configuration.
     * @param synchronous  Determines whether injection should be performed synchronously or asynchronously (see above for details).
     *
     * @throws OwnIdException If an error occurs during injection (see "Error Handling" above).
     * @throws IllegalStateException if called on a non-main thread or when attempting to attach WebView more than once.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @MainThread
    @SuppressLint("RequiresFeature")
    @Throws(OwnIdException::class, IllegalStateException::class)
    private suspend fun injectInto(webView: WebView, allowedOriginRules: Set<String>, synchronous: Boolean) {
        OwnIdInternalLogger.logD(this, "injectInto", "Synchronous: $synchronous")

        coroutineContext.ensureActive()

        try {
            check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
            check(this.webView == null) { "Bridge already attached to WebView" }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER).not()) {
                throw OwnIdException("Injection failed: WebViewFeature.WEB_MESSAGE_LISTENER not supported")
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT).not()) {
                throw OwnIdException("Injection failed: WebViewFeature.DOCUMENT_START_SCRIPT not supported")
            }

            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl

            if (synchronous) {
                if (ownIdCore.configuration.isServerConfigurationSet.not()) {
                    ownIdCore.configurationService.ensureConfigurationSet {
                        if (ownIdCore.configuration.isServerConfigurationSet) {
                            val validOriginRules = ownIdCore.configuration.server.origin.plus(allowedOriginRules)
                                .mapNotNull { urlString -> urlString.asHttpsHostOrNull() }
                                .toSet()

                            val message = "Configuration updated. Setting new origin rules: $validOriginRules"
                            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeImpl, "injectInto", message)
                            this@OwnIdWebViewBridgeImpl.allowedOriginRules = validOriginRules.toList()
                        }
                    }
                }
            } else {
                ownIdCore.configurationService.ensureConfigurationSet()
                coroutineContext.ensureActive()
            }

            val rawOriginRules = mutableSetOf<String>().apply {
                addAll(allowedOriginRules)
                if (ownIdCore.configuration.isServerConfigurationSet) addAll(ownIdCore.configuration.server.origin)
            }.toSet()

            val validOriginRules = rawOriginRules
                .mapNotNull { urlString -> urlString.asHttpsHostOrNull() }
                .toSet()
                .ifEmpty {
                    if (rawOriginRules.isNotEmpty()) throw OwnIdException("Injection failed: No valid origin rules found")
                    if (synchronous.not()) throw OwnIdException("Injection failed: No valid origin rules found")
                    setOf("*")
                }

            this.bridgeJob = SupervisorJob(coroutineContext.job.parent!!).apply { invokeOnCompletion { close() } }
            this.webView = webView

            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeImpl, "injectInto", "Attaching to origin: $validOriginRules")

            WebViewCompat.addWebMessageListener(webView, "__ownidNativeBridgeHandler", validOriginRules, webMessageListener)
            WebViewCompat.addDocumentStartJavaScript(webView, ownIdNativeBridgeJS, validOriginRules)

            this.allowedOriginRules = validOriginRules.toList()

            OwnIdInternalLogger.logD(this, "injectInto", "Namespaces attached: $features")
        } catch (cause: Throwable) {
            bridgeJob?.cancel() ?: close()

            if (cause is CancellationException) throw cause

            OwnIdInternalLogger.logW(this, "injectInto", cause.message, cause)
            throw OwnIdException.map("Injection failed: ${cause.message}", cause)
        }
    }

    @MainThread
    internal fun close() {
        OwnIdInternalLogger.logD(this, "close", "Invoked")
        bridgeJob = null
        webView = null
        allowedOriginRules = emptyList()
        callbackMap = emptyMap()
    }

    @MainThread
    internal fun setCallback(namespaceHandler: NamespaceHandler, callback: BridgeCallback<*, *>?) {
        OwnIdInternalLogger.logD(this, "setCallback", "Namespace: ${namespaceHandler.namespace}")

        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        callbackMap = if (callback != null) {
            callbackMap.plus(namespaceHandler to callback)
        } else {
            callbackMap.filterKeys { it != namespaceHandler }
        }
    }

    internal companion object {
        internal fun String.asHttpsHostOrNull(): String? = runCatching { this@asHttpsHostOrNull.toHttpUrl() }
            .map { if (it.isHttps) "https://${it.toHostHeader()}" else null }
            .getOrNull()
    }
}