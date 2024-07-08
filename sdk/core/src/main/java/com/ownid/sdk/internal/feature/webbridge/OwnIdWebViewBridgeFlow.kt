package com.ownid.sdk.internal.feature.webbridge

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.webflow.OwnIdFlowFeature
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeFlow : OwnIdWebViewBridgeImpl.Namespace {
    private const val ON_ACCOUNT_NOT_FOUND = "onAccountNotFound"
    private const val ON_LOGIN = "onLogin"
    private const val ON_CLOSE = "onClose"
    private const val ON_ERROR = "onError"

    override val name: String = "FLOW"
    override val actions: Array<String> = arrayOf(ON_ACCOUNT_NOT_FOUND, ON_LOGIN, ON_CLOSE, ON_ERROR)

    @UiThread
    override fun invoke(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        if (bridgeContext.isCanceled()) {
            OwnIdInternalLogger.logI(this, "invoke: $action", "Operation canceled")
            return
        }

        try {
            if (ON_ACCOUNT_NOT_FOUND.equals(action, ignoreCase = true)) {
                val value = requireNotNull(params) { "Unexpected: params=null" }
                bridgeContext.sendResult(OwnIdFlowFeature.Result(OwnIdFlowFeature.Result.Type.ON_ACCOUNT_NOT_FOUND, value))
                bridgeContext.launchOnMainThread { invokeSuccessCallback("{}") }
                return
            }

            if (ON_LOGIN.equals(action, ignoreCase = true)) {
                val value = requireNotNull(params) { "Unexpected: params=null" }
                bridgeContext.sendResult(OwnIdFlowFeature.Result(OwnIdFlowFeature.Result.Type.ON_LOGIN, value))
                bridgeContext.launchOnMainThread { invokeSuccessCallback("{}") }
                return
            }

            if (ON_CLOSE.equals(action, ignoreCase = true)) {
                bridgeContext.sendResult(OwnIdFlowFeature.Result(OwnIdFlowFeature.Result.Type.ON_CLOSE))
                bridgeContext.launchOnMainThread { invokeSuccessCallback("{}") }
                return
            }

            if (ON_ERROR.equals(action, ignoreCase = true)) {
                runCatching {
                    val value = params ?: """{"errorMessage":"Unexpected: params=null"}"""
                    bridgeContext.sendResult(OwnIdFlowFeature.Result(OwnIdFlowFeature.Result.Type.ON_ERROR, value))
                }.onFailure {
                    OwnIdInternalLogger.logW(this, "invoke: $action", it.message, it)
                }
                bridgeContext.launchOnMainThread { invokeSuccessCallback("{}") }
                return
            }

            throw IllegalArgumentException("OwnIdWebViewBridgeFlow: Unsupported action: '$action'")
        } catch (cause: Throwable) {
            OwnIdInternalLogger.logW(this, "invoke: $action", cause.message, cause)

            val result = JSONObject()
                .put("type", cause::class.java.simpleName)
                .put("errorCode", "ownIdWebViewBridgeFlowError")
                .put("errorMessage", cause.message)

            bridgeContext.launchOnMainThread { invokeErrorCallback(result) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(ClassCastException::class)
    private fun OwnIdWebViewBridgeContext.sendResult(result: OwnIdFlowFeature.Result) {
        (callback as OwnIdWebViewBridgeImpl.BridgeCallback<OwnIdFlowFeature.Result, OwnIdException>).onResult(result)
    }

//    @Suppress("UNCHECKED_CAST")
//    @Throws(ClassCastException::class)
//    private fun OwnIdWebViewBridgeContext.sendError(error: OwnIdException) {
//        (callback as OwnIdWebViewBridgeImpl.BridgeCallback<OwnIdFlowFeature.Result, OwnIdException>).onError(error)
//    }
}