package com.ownid.sdk.internal.feature.webflow

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.coroutineScope
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.OwnIdHiddenActivity
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import com.ownid.sdk.internal.feature.webbridge.handler.OwnIdWebViewBridgeFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@InternalOwnIdAPI
internal class OwnIdFlowFeatureWebView : OwnIdFlowFeature {

    internal companion object {
        @VisibleForTesting
        internal const val KEY_FLOW_INTENT = "com.ownid.sdk.internal.flow.KEY_FLOW_INTENT"

        internal fun createIntent(context: Context): Intent =
            OwnIdHiddenActivity.createIntent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(KEY_FLOW_INTENT, true)

        internal fun isThisFeature(intent: Intent): Boolean =
            intent.getBooleanExtra(KEY_FLOW_INTENT, false)
    }

    internal object JsConstants {
        internal const val KEY_WEB_VIEW_TAG = "com.ownid.sdk.internal.flow.KEY_WEB_VIEW_TAG"
        internal const val DEFAULT_WEBVIEW_URL = "https://webview.ownid.com"
        private const val OWNID_SCHEME = "ownid"
        private const val ON_JS_LOAD_ERROR = "on-js-load-error"
        private const val ON_JS_EXCEPTION = "on-js-exception"
        private const val DEFAULT_WEBVIEW_HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title></title>
  <link id="webapp-icon" rel="icon" type="image/png" href="https://cdn.prod.website-files.com/63e207687d9e033189f3c3f1/643fe358bb66c2a656709593_OwnID%20icon.png">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
   .spinner {--ownid-spinner-overlay-bg-color: #fff;--ownid-spinner-bg-color: rgba(133, 133, 133, .3);--ownid-spinner-bg-opasity: 1;--ownid-spinner-color: #858585;--ownid-spinner-size: 40px;position: absolute;z-index: 1;width: 100%;height: 100%;background-color: var(--ownid-spinner-overlay-bg-color);top: 0;left: 0;display: flex;justify-content: center;align-items: center;}.spinner svg {position: absolute;width: var(--ownid-spinner-size);height: var(--ownid-spinner-size);overflow: visible;}.spinner .bg {stroke: var(--ownid-spinner-bg-color);opacity: var(--ownid-spinner-bg-opasity);}.spinner .sp {stroke-linecap: round;stroke: var(--ownid-spinner-color);animation: animation 2s cubic-bezier(0.61, 0.24, 0.44, 0.79) infinite;}.spinner .bg, .spinner .sp {fill: none;stroke-width: 15px;}.spinner .sp-svg {animation: rotate 2s cubic-bezier(0.61, 0.24, 0.44, 0.79) infinite;}@keyframes animation {0% {stroke-dasharray: 1 270;stroke-dashoffset: 70;}50% {stroke-dasharray: 80 270;stroke-dashoffset: 220;}100% {stroke-dasharray: 1 270;stroke-dashoffset: 70;}}@keyframes rotate {100% {transform: rotate(720deg);}}
  </style>
  <script type="text/javascript">
    window.gigya = {};
    window.OWNID_NATIVE_WEBVIEW = true;
    window.ownid = async (...a) => ((window.ownid.q = window.ownid.q || []).push(a), {error: null, data: null});
    function onJSException(ex) { document.location.href = 'ownid://on-js-exception?ex=' + encodeURIComponent(ex); }
    function onJSLoadError() { document.location.href = 'ownid://on-js-load-error'; }
    setTimeout(function () { if (!window.ownid?.sdk) onJSLoadError(); }, 10000);
    window.onerror = (errorMsg) => onJSException(errorMsg);
    var interval = setInterval(() => { if (window.ownid?.sdk) { clearInterval(interval); window.onerror = () => {}; } }, 500);
  </script>
</head>
<body>
<div class="spinner">
  <svg viewBox="0 0 100 100"><circle class="bg" r="42.5" cx="50" cy="50"></circle></svg>
  <svg class="sp-svg" viewBox="0 0 100 100"><circle class="sp" r="42.5" cx="50" cy="50"></circle></svg>
</div>
<script src="https://cdn.OWNID-ENVownidOWNID-REGION.com/sdk/OWNID-APPID" type="text/javascript" onerror="onJSLoadError()"></script>
<script>ownid('start', { language: window.navigator.languages || 'en', animation: false });</script>
</body>
</html>"""

        internal fun getDefaultHTML(ownIdAppId: String, ownIdEnvironment: String, region: String): String = DEFAULT_WEBVIEW_HTML_TEMPLATE
            .replace("OWNID-APPID", ownIdAppId)
            .replace("OWNID-ENV", ownIdEnvironment)
            .replace("OWNID-REGION", region)

        internal fun Uri.isJSLoadError(): Boolean = isOwnIdScheme() && ON_JS_LOAD_ERROR.equals(host, ignoreCase = true)

        internal fun Uri.isJSException(): Boolean = isOwnIdScheme() && ON_JS_EXCEPTION.equals(host, ignoreCase = true)

        private fun Uri.isOwnIdScheme(): Boolean = OWNID_SCHEME.equals(scheme, ignoreCase = true)
    }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?) {
        OwnIdInternalLogger.logD(this, "onCreate", "Invoked")

        OwnIdWebViewBridgeFlow.addInvokeOnClose {
            OwnIdInternalLogger.logD(this@OwnIdFlowFeatureWebView, "invokeOnClose", "Closing activity")
            activity.close()
        }

        activity.enableEdgeToEdge()
        activity.window.decorView.setBackgroundColor(Color.BLACK)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = false

        activity.lifecycle.coroutineScope.launch {
            try {
                val ownIdCore = OwnId.instance.ownIdCore as OwnIdCoreImpl

                ownIdCore.configurationService.ensureConfigurationSet()
                ensureActive()
                val configuration = ownIdCore.configuration

                val webView = WebView(activity).apply {
                    tag = JsConstants.KEY_WEB_VIEW_TAG

                    settings.apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        userAgentString = configuration.userAgent
                    }

                    webViewClient = OwnIdFlowWebViewClient(this) { error -> OwnIdWebViewBridgeFlow.sendErrorEvent(error) }
                }

                activity.setContentView(webView, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                ViewCompat.setOnApplyWindowInsetsListener(webView) { v, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    v.updateLayoutParams<MarginLayoutParams> {
                        topMargin = insets.top
                        leftMargin = insets.left
                        bottomMargin = insets.bottom
                        rightMargin = insets.right
                    }
                    WindowInsetsCompat.CONSUMED
                }

                onBackPressedCallback = object : OnBackPressedCallback(enabled = true) {
                    override fun handleOnBackPressed() {
                        OwnIdInternalLogger.logD(this@OwnIdFlowFeatureWebView, "OnBackPressedCallback.handleOnBackPressed", "Invoked")
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            OwnIdWebViewBridgeFlow.sendCloseEvent()
                        }
                    }
                }
                activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)

                val eliteOptions = OwnIdWebViewBridgeFlow.options
                val webViewSettings = configuration.server.webViewSettings
                val baseUrl = eliteOptions?.webView?.baseUrl ?: webViewSettings?.baseUrl ?: JsConstants.DEFAULT_WEBVIEW_URL
                val html = eliteOptions?.webView?.html ?: webViewSettings?.html ?: JsConstants.getDefaultHTML(configuration.appId, configuration.env, configuration.region)

                OwnIdWebViewBridgeImpl(null, null).injectInto(webView, setOf(baseUrl), true)
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", null, null)
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this@OwnIdFlowFeatureWebView, "onCreate", cause.message, cause)

                if (cause is CancellationException) {
                    OwnIdWebViewBridgeFlow.sendCloseEvent()
                } else {
                    val error = OwnIdException.map("OwnIdFlowFeatureWebView.onCreate: ${cause.message}", cause)
                    OwnIdWebViewBridgeFlow.sendErrorEvent(error)
                }
            }
        }
    }

    override fun onDestroy(activity: AppCompatActivity) {
        OwnIdInternalLogger.logD(this@OwnIdFlowFeatureWebView, "onDestroy", "Invoked")
        if (::onBackPressedCallback.isInitialized) onBackPressedCallback.remove()
        activity.findViewById<ViewGroup>(android.R.id.content)?.findViewWithTag<WebView>(JsConstants.KEY_WEB_VIEW_TAG)?.apply {
            loadUrl("about:blank")
            webViewClient = object : WebViewClient() {}
        }
    }

    @Suppress("DEPRECATION")
    private fun AppCompatActivity.close() {
        finish()
        overridePendingTransition(0, 0)
    }
}