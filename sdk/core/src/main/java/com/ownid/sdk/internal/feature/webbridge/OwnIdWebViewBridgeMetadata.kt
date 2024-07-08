package com.ownid.sdk.internal.feature.webbridge

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeMetadata : OwnIdWebViewBridgeImpl.Namespace {
    private const val GET = "get"

    override val name: String = "METADATA"
    override val actions: Array<String> = arrayOf(GET)

    @UiThread
    override fun invoke(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        if (bridgeContext.isCanceled()) {
            OwnIdInternalLogger.logI(this, "invoke: $action", "Operation canceled")
            return
        }

        try {
            if (GET.equals(action, ignoreCase = true)) {
                val metadataJson = JSONObject().put("correlationId", bridgeContext.ownIdCore.correlationId)
                bridgeContext.launchOnMainThread { invokeSuccessCallback(metadataJson.toString()) }
                return
            }

            throw IllegalArgumentException("OwnIdWebViewBridgeMetadata.invoke: Unsupported action: '$action'")
        } catch (cause: Throwable) {
            OwnIdInternalLogger.logW(this, "invoke: $action", cause.message, cause)

            val result = JSONObject()
                .put("name", "OwnIdWebViewBridgeMetadata")
                .put("type", cause::class.java.simpleName)
                .put("message", cause.message)

            bridgeContext.launchOnMainThread { invokeErrorCallback(result) }
        }
    }
}