package com.ownid.sdk.internal.feature.webbridge.handler

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeMetadata : OwnIdWebViewBridgeImpl.NamespaceHandler {
    private const val GET = "get"

    override val namespace: OwnIdWebViewBridge.Namespace = OwnIdWebViewBridge.Namespace.METADATA
    override val actions: Array<String> = arrayOf(GET)

    @UiThread
    override fun handle(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        bridgeContext.launch {
            try {
                if (GET.equals(action, ignoreCase = true)) {
                    val metadataJson = JSONObject().put("correlationId", bridgeContext.ownIdCore.correlationId)
                    bridgeContext.finishWithSuccess(metadataJson.toString())

                    return@launch
                }

                throw IllegalArgumentException("OwnIdWebViewBridgeMetadata: Unsupported action: '$action'")
            } catch (cause: CancellationException) {
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeMetadata, cause)
                throw cause
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeMetadata, "invoke: $action", cause.message, cause)
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeMetadata, cause)
            }
        }
    }
}