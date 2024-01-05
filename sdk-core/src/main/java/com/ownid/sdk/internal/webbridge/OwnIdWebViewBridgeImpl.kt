package com.ownid.sdk.internal.webbridge

import android.annotation.SuppressLint
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.webbridge.OwnIdWebViewBridgeImpl.JsCallback
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

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

    private val nameSpaces = arrayOf<Namespace>(OwnIdWebViewBridgeFido)
    private val features = JSONObject().apply { nameSpaces.forEach { put(it.name, JSONArray(it.actions)) } }.toString()
    private val ownIdNativeBridgeJS =
        """window.__ownidNativeBridge = {
  getNamespaces: function getNamespaces() { return '""" + features + """'; },
  invokeNative: function invokeNative(namespace, action, callbackPath, params) {
    try {
      window.__ownidNativeBridgeHandler.postMessage(JSON.stringify({ namespace, action, callbackPath, params }));
    } catch (error) {
      console.error(error);
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
                val data = JSONObject(message.data ?: throw IllegalArgumentException("Parameter required: 'message.data'"))
                val callbackPath = data.optString("callbackPath").ifBlank { throw IllegalArgumentException("Parameter required: 'callbackPath'") }
                val namespace = data.optString("namespace")
                val action = data.optString("action")
                val params = data.optString("params").ifBlank { null }

                nameSpaces.firstOrNull { it.name.equals(namespace, ignoreCase = true) }?.run {
                    val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl

                    val callerContext = OwnIdWebViewBridgeContext(
                        webView, ownIdCore, canceller, sourceOrigin, allowedOriginRules, isMainFrame, callbackPath, WeakReference(jsCallback)
                    )

                    invoke(callerContext, action, params)

                } ?: OwnIdInternalLogger.logI(this@OwnIdWebViewBridgeImpl, "onPostMessage", "No namespace found: '$namespace'")
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logE(this@OwnIdWebViewBridgeImpl, "onPostMessage", cause.message, cause)
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

    @MainThread
    @SuppressLint("RequiresFeature")
    public override fun injectInto(webView: WebView, allowedOriginRules: Set<String>, owner: LifecycleOwner?) {
        OwnIdInternalLogger.logD(this, "attach", "Invoked")

        check(Looper.getMainLooper().isCurrentThread) { "Only main thread allowed" }

        try {
            check(this.webView == null) { "Bridge already attached to WebView" }
            require(owner != null) { "WebView lifecycle owner must be set" }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER).not()) {
                OwnIdInternalLogger.logW(this, "injectInto", "WebViewFeature.WEB_MESSAGE_LISTENER not supported. Ignoring.")
                return
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT).not()) {
                OwnIdInternalLogger.logW(this, "injectInto", "WebViewFeature.DOCUMENT_START_SCRIPT not supported. Ignoring.")
                return
            }

            OwnIdInternalLogger.logD(this, "injectInto", "Attaching WebView to '$instanceName' instance")

            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    clean()
                }
            })

            this.webView = webView
            this.canceller = CancellationSignal()

            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl
            ownIdCore.configurationService.ensureConfigurationSet {
                mapCatching {
                    val combinedAllowedOriginRules = ownIdCore.configuration.server.origin
                        .plus(allowedOriginRules.filter { it.isNotBlank() })
                        .mapNotNull {
                            val url = it.trim()
                            if (url == "*") return@mapNotNull null
                            val uri = Uri.parse(url)
                            when {
                                uri.isAbsolute.not() -> "https://$url"
                                uri.scheme.equals("https", ignoreCase = true) -> url
                                else -> null
                            }
                        }
                        .toSet()

                    WebViewCompat.addWebMessageListener(webView, "__ownidNativeBridgeHandler", combinedAllowedOriginRules, webMessageListener)
                    WebViewCompat.addDocumentStartJavaScript(webView, ownIdNativeBridgeJS, combinedAllowedOriginRules)

                    this@OwnIdWebViewBridgeImpl.allowedOriginRules = combinedAllowedOriginRules.toList()
                }.onFailure { OwnIdInternalLogger.logE(this, "injectInto", it.message, it) }
            }
        } catch (cause: Throwable) {
            OwnIdInternalLogger.logE(this, "injectInto", cause.message, cause)
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
    }
}