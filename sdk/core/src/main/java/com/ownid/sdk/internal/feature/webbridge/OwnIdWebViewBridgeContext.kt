package com.ownid.sdk.internal.feature.webbridge

import android.net.Uri
import android.webkit.WebView
import androidx.annotation.MainThread
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.json.JSONObject

@InternalOwnIdAPI
internal class OwnIdWebViewBridgeContext(
    val ownIdCore: OwnIdCoreImpl,
    val webView: WebView,
    val bridgeJob: Job,
    val allowedOriginRules: List<String>,
    val sourceOrigin: Uri,
    val isMainFrame: Boolean,
    val callbackPath: String,
) : CoroutineScope by CoroutineScope(SupervisorJob(bridgeJob) + Dispatchers.Main.immediate) {

    @MainThread
    @Throws(IllegalStateException::class)
    internal fun ensureMainFrame() {
        if (isMainFrame.not()) {
            throw IllegalStateException("Requests from subframes are not supported")
        }
    }

    @MainThread
    @Throws(IllegalStateException::class)
    internal fun ensureOriginSecureScheme() {
        if (sourceOrigin.scheme?.lowercase() != "https") {
            throw IllegalStateException("WebAuthn not permitted for current URL: $sourceOrigin")
        }
    }

    @MainThread
    @Throws(IllegalStateException::class)
    internal fun ensureAllowedOrigin() {
        val sourceOriginHost = sourceOrigin.host?.lowercase()
            ?: throw IllegalStateException("WebAuthn not permitted for current origin: $sourceOrigin")

        allowedOriginRules.asSequence()
            .mapNotNull { Uri.parse(it).host?.lowercase() }
            .forEach { allowHost ->
                if (allowHost == sourceOriginHost) return
                if (allowHost.startsWith("*") && sourceOriginHost.endsWith(allowHost.drop(1))) return
            }

        throw IllegalStateException("WebAuthn not permitted for current origin: $sourceOriginHost, allowed: $allowedOriginRules")
    }

    @MainThread
    internal fun finishWithSuccess(result: String) {
        if (isActive.not()) {
            OwnIdInternalLogger.logI(this, "finishWithSuccess", "Operation canceled by caller: $callbackPath")
            return
        }

        OwnIdInternalLogger.logD(this, "finishWithSuccess", "callbackPath: $callbackPath")
        webView.evaluateJavascript("javascript:$callbackPath($result)", null)

        cancel()
    }

    @MainThread
    internal fun finishWithError(handler: OwnIdWebViewBridgeImpl.NamespaceHandler, error: Throwable) {
        if (isActive.not()) {
            OwnIdInternalLogger.logI(this, "finishWithError", "Operation canceled")
            return
        }

        OwnIdInternalLogger.logD(this, "finishWithSuccess", "callbackPath: $callbackPath")
        val result = JSONObject().put(
            "error",
            JSONObject()
                .put("name", handler::class.java.simpleName)
                .put("type", error::class.java.simpleName)
                .put("message", error.message)
        ).toString()
        webView.evaluateJavascript("javascript:$callbackPath($result)", null)

        cancel()
    }

    @MainThread
    internal fun sendMetric(flowType: OwnIdNativeFlowType, type: Metric.EventType, action: String, errorMessage: String? = null) {
        ownIdCore.eventsService.sendMetric(flowType, type, action, errorMessage = errorMessage)
    }
}