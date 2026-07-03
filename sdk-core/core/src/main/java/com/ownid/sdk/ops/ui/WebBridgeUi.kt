package com.ownid.sdk.ops.ui

import android.net.Uri
import android.webkit.WebView
import androidx.annotation.ColorInt
import androidx.annotation.UiThread
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.OperationID
import com.ownid.sdk.model.Reason
import com.ownid.sdk.ops.OperationController
import com.ownid.sdk.ops.OperationUi
import com.ownid.sdk.ops.WebBridgeOperation
import com.ownid.sdk.ops.WebBridgeOperationFailure
import com.ownid.sdk.toStringPrefix
import com.ownid.sdk.toStringSuffix
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * UI capability for [WebBridgeOperation].
 *
 * The operation publishes [UiState] values, and the hosting [Container] renders and tears down the WebView based on
 * those values. The UI layer owns Android WebView presentation, back navigation, and platform startup errors; operation
 * settlement remains owned by [WebBridgeOperation].
 *
 * SDK-managed WebBridge UI keeps the OwnID flow document in the embedded WebView, blocks local/script schemes, opens
 * external navigations outside the flow, and reports JavaScript bootstrap failures, render-process loss, and load
 * failures through the operation callbacks in [UiState].
 */
internal interface WebBridgeUi : OperationUi {

    /**
     * Immutable WebView configuration and action callback set.
     *
     * @property baseUrl Base URL passed to [WebView.loadDataWithBaseURL] for the HTML page and used to derive the
     *   WebBridge page origin.
     * @property html Raw HTML rendered by the SDK-owned WebView.
     * @property userAgent User-Agent string applied to the WebView before loading.
     * @property statusBarColor Color for the hosting activity status bar, or `null` for the container default.
     * @property navigationBarColor Color for the hosting activity navigation bar, or `null` for the container default.
     * @property doWebViewBridgeInject Callback that prepares WebBridge communication for a WebView before HTML load.
     *   Attachment failures must be reported through [onWebViewTerminalError].
     * @property onWebViewDetach Callback invoked when the WebView is detached from the container. The operation uses it
     *   to release WebBridge resources and treat active-session detach as user cancellation.
     * @property onWebViewTerminalError Callback invoked for unrecoverable WebView/bridge errors, JavaScript bootstrap
     *   failures, render-process termination, or HTML load failures.
     * @property onWebViewCancel Callback invoked when the user cancels or closes the WebView flow.
     */
    data class UiState(
        val baseUrl: String,
        val html: String,
        val userAgent: String,
        @ColorInt val statusBarColor: Int?,
        @ColorInt val navigationBarColor: Int?,
        @UiThread val doWebViewBridgeInject: (webView: WebView) -> Unit,
        @UiThread val onWebViewDetach: () -> Unit,
        @UiThread val onWebViewTerminalError: (error: Throwable?, details: String?) -> Unit,
        @UiThread val onWebViewCancel: (reason: Reason) -> Unit
    )

    /** Platform container, such as the hidden activity delegate, that hosts the WebView. */
    interface Container {
        /**
         * Shows the WebView container for the given operation instance.
         *
         * Implementations must report presentation/startup failures through [onShowError].
         */
        fun show(instanceName: InstanceName, operationID: OperationID, onShowError: (error: Throwable?, details: String?) -> Unit)
    }

    /**
     * Requests UI presentation for [controller].
     *
     * Implementations must not throw from this method. Report recoverable startup or presentation failures through [onStartError] instead.
     *
     * [onStartError] is invoked when container presentation fails, either synchronously or asynchronously through
     * container error callbacks. The operation maps that callback to a [WebBridgeOperationFailure.Ui] failure.
     */
    fun start(controller: OperationController<Unit, WebBridgeOperationFailure>, onStartError: (error: Throwable?, details: String?) -> Unit)

    /** Default constants and helpers for the WebView content and URL scheme handling. */
    companion object Defaults {
        private const val OWNID_SCHEME = "ownid"
        private const val ON_JS_LOAD_ERROR = "on-js-load-error"
        private const val ON_JS_EXCEPTION = "on-js-exception"

        /** Default fallback base URL for the OwnID WebView page. */
        const val WEBVIEW_URL = "https://webview.ownid.com"
        private const val WEBVIEW_HTML_TEMPLATE = """
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
<script src="OWNID-CDN-URL" type="text/javascript" onerror="onJSLoadError()"></script>
<script>ownid('start', { language: window.navigator.languages || 'en', animation: false });</script>
</body>
</html>"""

        /** Returns the WebView HTML template with `OWNID-CDN-URL` resolved from [configuration]. */
        fun getHTML(configuration: OwnIdConfiguration): String = WEBVIEW_HTML_TEMPLATE.replace("OWNID-CDN-URL", configuration.cdnUrl())

        /**
         * Builds the OwnID SDK script URL for this configuration.
         *
         * Uses [OwnIdConfiguration.rootUrl] when provided; otherwise resolves the default CDN host by environment and
         * region, then appends `sdk/{appId}`.
         */
        fun OwnIdConfiguration.cdnUrl(): String = when (val rootUrl = rootUrl) {
            null -> "https://cdn${toStringPrefix()}.ownid${region.toStringSuffix()}.com".toHttpUrl()
            else -> rootUrl.toHttpUrl()
        }
            .newBuilder()
            .addPathSegments("sdk/$appId")
            .build()
            .toString()

        /** Returns `true` when this URI matches the template's `ownid://on-js-load-error` callback URL. */
        fun Uri.isJSLoadError(): Boolean = isOwnIdScheme() && ON_JS_LOAD_ERROR.equals(host, ignoreCase = true)

        /** Returns `true` when this URI matches the template's `ownid://on-js-exception` callback URL. */
        fun Uri.isJSException(): Boolean = isOwnIdScheme() && ON_JS_EXCEPTION.equals(host, ignoreCase = true)

        /** Returns `true` if this URI uses the `ownid://` scheme. */
        fun Uri.isOwnIdScheme(): Boolean = OWNID_SCHEME.equals(scheme, ignoreCase = true)
    }
}
