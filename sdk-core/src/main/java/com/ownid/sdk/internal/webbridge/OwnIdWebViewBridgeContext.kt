package com.ownid.sdk.internal.webbridge

import android.net.Uri
import android.os.CancellationSignal
import android.webkit.WebView
import androidx.annotation.MainThread
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.Metric
import com.ownid.sdk.internal.flow.OwnIdFlowType
import org.json.JSONObject
import java.lang.ref.WeakReference

@InternalOwnIdAPI
internal class OwnIdWebViewBridgeContext(
    val webView: WebView,
    val ownIdCore: OwnIdCoreImpl,
    val canceller: CancellationSignal,
    val sourceOrigin: Uri,
    val allowedOriginRules: List<String>,
    val isMainFrame: Boolean,
    val callbackPath: String,
    val callbackWeak: WeakReference<OwnIdWebViewBridgeImpl.JsCallback>
) {
    @MainThread
    fun isCanceled(): Boolean = canceller.isCanceled

    @MainThread
    @Throws(IllegalStateException::class)
    fun ensureMainFrame() {
        if (isMainFrame.not())
            throw IllegalStateException("Requests from subframes are not supported")
    }

    @MainThread
    @Throws(IllegalStateException::class)
    fun ensureOriginSecureScheme() {
        if (sourceOrigin.scheme?.lowercase() != "https")
            throw IllegalStateException("WebAuthn not permitted for current URL: $sourceOrigin")
    }

    @MainThread
    @Throws(IllegalStateException::class)
    fun ensureAllowedOrigin() {
        val sourceOriginHost = sourceOrigin.host?.lowercase()
            ?: throw IllegalStateException("WebAuthn not permitted for current origin: $sourceOrigin")

        allowedOriginRules.asSequence()
            .mapNotNull { Uri.parse(it).host?.lowercase() }
            .forEach { allowHost ->
                if (allowHost == sourceOriginHost) return
                if (allowHost.startsWith("*") && sourceOriginHost.endsWith(allowHost.drop(1))) return
            }

        throw IllegalStateException("WebAuthn not permitted for current origin: $sourceOriginHost")
    }

    @MainThread
    fun invokeSuccessCallback(result: String) {
        if (isCanceled()) {
            OwnIdInternalLogger.logI(this, "invokeSuccessCallback", "Operation canceled")
            return
        }
        callbackWeak.get()?.invoke(callbackPath, result) ?: run {
            OwnIdInternalLogger.logI(this, "invokeSuccessCallback", "No callback available")
        }
    }

    @MainThread
    fun invokeErrorCallback(error: JSONObject) {
        if (isCanceled()) {
            OwnIdInternalLogger.logI(this, "invokeErrorCallback", "Operation canceled")
            return
        }
        callbackWeak.get()?.invoke(callbackPath, JSONObject().put("error", error).toString()) ?: run {
            OwnIdInternalLogger.logI(this, "invokeErrorCallback", "No callback available")
        }
    }

    @MainThread
    fun sendMetric(flowType: OwnIdFlowType, type: Metric.EventType, action: String? = null, errorMessage: String? = null) {
        ownIdCore.eventsService.sendMetric(flowType, type, action, null, this::class.java.simpleName, errorMessage)
    }
}