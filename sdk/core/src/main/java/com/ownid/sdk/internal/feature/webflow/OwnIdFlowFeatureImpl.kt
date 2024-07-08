package com.ownid.sdk.internal.feature.webflow

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.ResultReceiver
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.coroutineScope
import com.ownid.sdk.FlowResult
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.SessionAdapter
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdLoginId
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.OwnIdHiddenActivity
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeFlow
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import kotlin.coroutines.cancellation.CancellationException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdFlowFeatureImpl : OwnIdFlowFeature {

    internal companion object {
        @VisibleForTesting internal const val KEY_FLOW_INTENT = "com.ownid.sdk.internal.flow.KEY_FLOW_INTENT"
        @VisibleForTesting internal const val KEY_INSTANCE_NAME = "com.ownid.sdk.internal.flow.KEY_INSTANCE_NAME"
        @VisibleForTesting internal const val KEY_RESULT_RECEIVER = "com.ownid.sdk.internal.flow.KEY_RESULT_RECEIVER"
        @VisibleForTesting internal const val KEY_BROADCAST_CLOSE_REQUEST = "com.ownid.sdk.internal.flow.KEY_BROADCAST_CLOSE_REQUEST"
        @VisibleForTesting internal const val KEY_RESPONSE_RESULT_TYPE = "com.ownid.sdk.internal.flow.KEY_RESPONSE_RESULT_TYPE"
        @VisibleForTesting internal const val KEY_RESPONSE_RESULT = "com.ownid.sdk.internal.flow.KEY_RESPONSE_RESULT"

        internal fun createIntent(
            context: Context, instanceName: InstanceName, resultReceiver: ResultReceiver
        ): Intent =
            OwnIdHiddenActivity.createIntent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(KEY_FLOW_INTENT, true)
                .putExtra(KEY_INSTANCE_NAME, instanceName.toString())
                .putExtra(KEY_RESULT_RECEIVER, resultReceiver.toIpcFriendlyResultReceiver())

        internal fun isThisFeature(intent: Intent): Boolean =
            intent.getBooleanExtra(KEY_FLOW_INTENT, false)

        internal fun sendCloseRequest(context: Context) =
            context.sendBroadcast(Intent(KEY_BROADCAST_CLOSE_REQUEST).setPackage(context.packageName))

        internal fun <T> decodeResult(resultCode: Int, resultData: Bundle, adapter: SessionAdapter<T>): FlowResult<T> = runCatching {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val type = resultData.getSerializableOrNull<OwnIdFlowFeature.Result.Type>(KEY_RESPONSE_RESULT_TYPE)
                        ?: throw OwnIdException("Error decoding result: [$resultCode] $resultData")

                    when (type) {
                        OwnIdFlowFeature.Result.Type.ON_ACCOUNT_NOT_FOUND ->
                            resultData.getString(KEY_RESPONSE_RESULT)?.toOnAccountNotFound()
                                ?: throw OwnIdException("Error decoding result: [$resultCode] $resultData")

                        OwnIdFlowFeature.Result.Type.ON_LOGIN ->
                            resultData.getString(KEY_RESPONSE_RESULT)?.toOnLogin(adapter)
                                ?: throw OwnIdException("Error decoding result: [$resultCode] $resultData")

                        OwnIdFlowFeature.Result.Type.ON_CLOSE -> FlowResult.OnClose

                        OwnIdFlowFeature.Result.Type.ON_ERROR ->
                            resultData.getString(KEY_RESPONSE_RESULT)?.let { FlowResult.OnError(OwnIdException(it)) }
                                ?: throw OwnIdException("Error decoding result: [$resultCode] $resultData")
                    }
                }

                Int.MAX_VALUE -> {
                    val error = resultData.getSerializableOrNull<OwnIdException>(KEY_RESPONSE_RESULT)
                        ?: throw OwnIdException("Error decoding result: [$resultCode] $resultData")
                    FlowResult.OnError(error)
                }

                else -> {
                    OwnIdInternalLogger.logW(this, "OwnIdFlowFeature.decodeResult", "[$resultCode] $resultData")
                    FlowResult.OnClose
                }
            }
        }.getOrElse {
            FlowResult.OnError(OwnIdException.map("OwnIdFlowFeature.decodeResult: ${it.message}", it))
        }

        private fun ResultReceiver.toIpcFriendlyResultReceiver(): ResultReceiver = Parcel.obtain().let { parcel ->
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            ResultReceiver.CREATOR.createFromParcel(parcel).also { parcel.recycle() }
        }

        private fun ResultReceiver.sendResult(result: OwnIdFlowFeature.Result) = send(
            Activity.RESULT_OK,
            Bundle().apply {
                putSerializable(KEY_RESPONSE_RESULT_TYPE, result.type)
                putString(KEY_RESPONSE_RESULT, result.value)
            }
        )

        private fun ResultReceiver.sendError(error: OwnIdException) =
            send(Int.MAX_VALUE, Bundle().apply { putSerializable(KEY_RESPONSE_RESULT, error) })

        @Suppress("DEPRECATION")
        private inline fun <reified T : Serializable> Bundle.getSerializableOrNull(key: String): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getSerializable(key, T::class.java) else getSerializable(key) as? T

        @Suppress("DEPRECATION")
        private inline fun <reified T : Parcelable> Intent.getParcelableOrNull(key: String): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

        @Throws(JSONException::class)
        private fun String.toOnAccountNotFound(): FlowResult.OnAccountNotFound = JSONObject(this).run {
            FlowResult.OnAccountNotFound(
                loginId = getString("loginId"),
                authToken = optString("authToken").ifBlank { null },
                ownIdData = optString("ownIdData").ifBlank { null }
            )
        }

        @Throws
        private fun <T> String.toOnLogin(adapter: SessionAdapter<T>): FlowResult.OnLogin<T> = JSONObject(this).run {
            val metadata = getJSONObject("metadata")
            FlowResult.OnLogin(
                session = adapter.transformOrThrow(getString("session")),
                loginId = metadata.getString("loginId"),
                authToken = metadata.getString("authToken")
            )
        }
    }

    internal object JsConstants {
        internal const val KEY_WEB_VIEW_TAG = "com.ownid.sdk.internal.flow.KEY_WEB_VIEW_TAG"
        private const val OWNID_SCHEME = "ownid"
        private const val ON_JS_LOAD_ERROR = "on-js-load-error"
        private const val ON_JS_EXCEPTION = "on-js-exception"

        internal fun Uri.isJSLoadError(): Boolean = isOwnIdScheme() && ON_JS_LOAD_ERROR.equals(host, ignoreCase = true)

        internal fun Uri.isJSException(): Boolean = isOwnIdScheme() && ON_JS_EXCEPTION.equals(host, ignoreCase = true)

        private fun Uri.isOwnIdScheme(): Boolean = OWNID_SCHEME.equals(scheme, ignoreCase = true)

        internal val defaultWebViewUrl = "https://webview.ownid.com"

        private val defaultWebViewHtmlTemplate = """
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
<script src="https://cdn.OWNID-ENVownid.com/sdk/OWNID-APPID" type="text/javascript" onerror="onJSLoadError()"></script>
<script>ownid('start', { language: window.navigator.languages[0] || 'en', animation: false });</script>
</body>
</html>""".trimIndent()

        internal fun getDefaultHTML(ownIdAppId: String, ownIdEnvironment: String): String = defaultWebViewHtmlTemplate
            .replace("OWNID-APPID", ownIdAppId)
            .replace("OWNID-ENV", ownIdEnvironment)
    }

    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?) {
        OwnIdInternalLogger.logD(this, "onCreate", "Invoked")

        val resultReceiver = activity.intent.getParcelableOrNull<ResultReceiver>(KEY_RESULT_RECEIVER)
        if (resultReceiver == null) {
            OwnIdInternalLogger.logE(this, "onCreate", "resultReceiver == null")
            activity.close()
            return
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == KEY_BROADCAST_CLOSE_REQUEST) {
                    OwnIdInternalLogger.logD(this@OwnIdFlowFeatureImpl, "BroadcastReceiver.onReceive", "Invoked")
                    activity.close()
                }
            }
        }
        ContextCompat.registerReceiver(
            activity, broadcastReceiver, IntentFilter(KEY_BROADCAST_CLOSE_REQUEST), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        activity.lifecycle.coroutineScope.launch {
            try {
                val instanceName = InstanceName(requireNotNull(activity.intent.getStringExtra(KEY_INSTANCE_NAME)))
                val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl

                ownIdCore.configurationService.ensureConfigurationSet()
                ensureActive()

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
                        userAgentString = ownIdCore.configuration.userAgent
                    }

                    webViewClient = FlowWebViewClient(this) { error ->
                        resultReceiver.sendError(error)
                        activity.close()
                    }
                }

                activity.setContentView(webView, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                onBackPressedCallback = object : OnBackPressedCallback(enabled = true) {
                    override fun handleOnBackPressed() {
                        OwnIdInternalLogger.logD(this@OwnIdFlowFeatureImpl, "OnBackPressedCallback.handleOnBackPressed", "Invoked")
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            resultReceiver.sendResult(OwnIdFlowFeature.Result(OwnIdFlowFeature.Result.Type.ON_CLOSE))
                            activity.close()
                        }
                    }
                }
                activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)

                // Called on UI thread by the injected JavaScript object
                val callback = object : OwnIdWebViewBridgeImpl.BridgeCallback<OwnIdFlowFeature.Result, OwnIdException> {
                    override fun onResult(result: OwnIdFlowFeature.Result) {
                        OwnIdInternalLogger.logD(this@OwnIdFlowFeatureImpl, "OwnIdCallback.onResult", "type: ${result.type}")
                        if (result.type == OwnIdFlowFeature.Result.Type.ON_LOGIN) {
                            if (result.value == null) return
                            val loginId = JSONObject(result.value).optJSONObject("metadata")?.optString("loginId")?.ifBlank { null }
                            if (loginId != null) {
                                activity.lifecycle.coroutineScope.launch { ownIdCore.saveLoginId(loginId) }
                            }
                        }
                        resultReceiver.sendResult(result)
                        activity.close()
                    }

                    override fun onError(error: OwnIdException) {
                        OwnIdInternalLogger.logD(this@OwnIdFlowFeatureImpl, "OwnIdCallback.onError", error.toString())
                        resultReceiver.sendError(error)
                        activity.close()
                    }
                }

                val webViewSettings = ownIdCore.configuration.server.webViewSettings
                val baseUrl = webViewSettings?.baseUrl ?: JsConstants.defaultWebViewUrl
                val html = webViewSettings?.html ?: JsConstants.getDefaultHTML(ownIdCore.configuration.appId, ownIdCore.configuration.env)

                OwnIdWebViewBridgeImpl(instanceName).apply {
                    setCallback(OwnIdWebViewBridgeFlow, callback)
                    injectInto(webView, setOf(baseUrl))
                }

                webView.loadDataWithBaseURL(baseUrl, html, "text/html", null, null)
            } catch (cause: Throwable) {
                if (cause is CancellationException) throw cause

                OwnIdInternalLogger.logW(this, "onCreate", cause.message, cause)
                resultReceiver.sendError(OwnIdException.map("OwnIdFlowFeature.onCreate: ${cause.message}", cause))
                activity.close()
            }
        }
    }

    override fun onDestroy(activity: AppCompatActivity) {
        OwnIdInternalLogger.logD(this, "onDestroy", "Invoked")
        if (::onBackPressedCallback.isInitialized) onBackPressedCallback.remove()
        if (::broadcastReceiver.isInitialized) activity.unregisterReceiver(broadcastReceiver)
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

    private suspend fun OwnIdCoreImpl.saveLoginId(loginId: String) {
        withContext(NonCancellable) {
            val ownIdLoginId = OwnIdLoginId(loginId)
            runCatching { repository.saveLoginId(ownIdLoginId) }
            val loginIdData = repository.getLoginIdData(ownIdLoginId)
            runCatching { repository.saveLoginIdData(loginIdData.copy(isOwnIdLogin = true)) }
        }
    }
}