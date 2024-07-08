package com.ownid.sdk.internal.feature.webbridge

import android.annotation.SuppressLint
import android.net.Uri
import android.os.CancellationSignal
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
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl.JsCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.internal.toHostHeader
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.coroutines.coroutineContext

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdWebViewBridgeImpl(private val instanceName: InstanceName) : OwnIdWebViewBridge {

    @InternalOwnIdAPI
    internal interface Namespace {
        val name: String
        val actions: Array<String>

        @UiThread
        fun invoke(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?)
    }

    @InternalOwnIdAPI
    internal fun interface JsCallback {
        @MainThread
        fun invoke(callbackPath: String, result: String)
    }

    @InternalOwnIdAPI
    internal interface BridgeCallback<R : Any?, E : Any> {
        fun onResult(result: R)
        fun onError(error: E)
    }

    private val nameSpaces = arrayOf(OwnIdWebViewBridgeMetadata, OwnIdWebViewBridgeFido, OwnIdWebViewBridgeFlow)
    private val features = JSONObject().apply { nameSpaces.forEach { put(it.name, JSONArray(it.actions)) } }.toString()
    private val ownIdNativeBridgeJS =
        """window.__ownidNativeBridge = {
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
};"""

    private val webMessageListener = object : WebViewCompat.WebMessageListener {
        @UiThread
        override fun onPostMessage(
            view: WebView, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, replyProxy: JavaScriptReplyProxy
        ) {
            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeImpl, "onPostMessage", "sourceOrigin: $sourceOrigin, isMainFrame: $isMainFrame")

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

            val canceller = this@OwnIdWebViewBridgeImpl.canceller
            if (canceller == null || canceller.isCanceled) {
                OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "onPostMessage", "Operation canceled by caller")
                return
            }

            val webView = this@OwnIdWebViewBridgeImpl.webView
            if (webView == null) {
                OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "onPostMessage", "WebView not available")
                return
            }

            try {
                val data = JSONObject(requireNotNull(message.data) { "Parameter required: 'message.data'" })
                val callbackPath = requireNotNull(data.optString("callbackPath").ifBlank { null }) { "Parameter required: 'callbackPath'" }
                val namespace = data.optString("namespace")
                val action = data.optString("action")
                val params = data.optString("params").ifBlank { null }

                nameSpaces.firstOrNull { it.name.equals(namespace, ignoreCase = true) }?.run {
                    val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl

                    val callerContext = OwnIdWebViewBridgeContext(
                        webView, ownIdCore, canceller, callbackMap[this], sourceOrigin, allowedOriginRules, isMainFrame, callbackPath, WeakReference(jsCallback)
                    )

                    invoke(callerContext, action, params)

                } ?: OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "onPostMessage", "No namespace found: '$namespace'")
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeImpl, "onPostMessage", cause.message, cause)
            }
        }
    }

    private val jsCallback = JsCallback { callbackPath, result ->
        val canceller = this.canceller
        if (canceller == null || canceller.isCanceled) {
            OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "jsCallback", "Operation canceled by caller: $callbackPath")
            return@JsCallback
        }

        val webView = this.webView
        if (webView == null) {
            OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "jsCallback", "WebView not available: $callbackPath")
            return@JsCallback
        }

        OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeImpl, "jsCallback", "callbackPath: $callbackPath")
        webView.evaluateJavascript("javascript:$callbackPath($result)", null)
    }

    @Volatile private var canceller: CancellationSignal? = null
    @Volatile private var webView: WebView? = null
    @Volatile private var allowedOriginRules: List<String> = emptyList()
    @Volatile private var callbackMap: Map<Namespace, BridgeCallback<*, *>> = emptyMap()

    @MainThread
    internal fun setCallback(namespace: Namespace, callback: BridgeCallback<*, *>?) {
        OwnIdInternalLogger.logD(this, "setCallback", "Namespace: ${namespace.name}")

        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        callbackMap = if (callback != null) {
            callbackMap.plus(namespace to callback)
        } else {
            callbackMap.filterKeys { it != namespace }
        }
    }

    @MainThread
    public override fun injectInto(webView: WebView, allowedOriginRules: Set<String>, owner: LifecycleOwner?, onResult: (OwnIdCallback<Unit>)?) {
        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }
        require(owner != null) { "WebView lifecycle owner must be set" }

        owner.lifecycle.coroutineScope.launch {
            try {
                injectInto(webView, allowedOriginRules)
                onResult?.invoke(Result.success(Unit))
            } catch (cause: Throwable) {
                if (cause is CancellationException) throw cause
                onResult?.invoke(Result.failure(OwnIdException.map("Injection failed: ${cause.message}", cause)))
            }
        }
    }

    /**
     * Function must be called within coroutine context bound to the WebView lifecycle.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @MainThread
    @SuppressLint("RequiresFeature")
    @Throws(OwnIdException::class)
    internal suspend fun injectInto(webView: WebView, allowedOriginRules: Set<String>) {
        OwnIdInternalLogger.logD(this, "injectInto", "Invoked")

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

            OwnIdInternalLogger.logD(this, "injectInto", "Attaching WebView to '$instanceName' instance")

            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl

            ownIdCore.configurationService.ensureConfigurationSet()

            val combinedAllowedOriginRules = ownIdCore.configuration.server.origin
                .plus(allowedOriginRules)
                .mapNotNull { urlString ->
                    runCatching { urlString.toHttpUrl() }
                        .recoverCatching { "https://$urlString".toHttpUrl() }
                        .map { if (it.isHttps) "https://${it.toHostHeader()}" else null }
                        .getOrNull()
                }
                .toSet()

            coroutineContext.job.parent?.invokeOnCompletion { clean() }
            coroutineContext.ensureActive()

            this.webView = webView
            this.canceller = CancellationSignal()

            WebViewCompat.addWebMessageListener(webView, "__ownidNativeBridgeHandler", combinedAllowedOriginRules, webMessageListener)
            WebViewCompat.addDocumentStartJavaScript(webView, ownIdNativeBridgeJS, combinedAllowedOriginRules)

            this@OwnIdWebViewBridgeImpl.allowedOriginRules = combinedAllowedOriginRules.toList()
        } catch (cause: Throwable) {
            clean()

            if (cause is CancellationException) throw cause

            OwnIdInternalLogger.logW(this, "injectInto", cause.message, cause)
            throw OwnIdException.map("Injection failed: ${cause.message}", cause)
        }
    }

    @MainThread
    private fun clean() {
        OwnIdInternalLogger.logD(this, "clean", "Invoked")

        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        this.canceller?.cancel()
        this.canceller = null
        this.webView = null
        this.allowedOriginRules = emptyList()
        this.callbackMap = emptyMap()
    }
}