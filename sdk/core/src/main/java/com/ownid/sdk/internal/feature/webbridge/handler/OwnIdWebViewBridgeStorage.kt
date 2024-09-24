package com.ownid.sdk.internal.feature.webbridge.handler

import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import com.ownid.sdk.AuthMethod
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
internal object OwnIdWebViewBridgeStorage : OwnIdWebViewBridgeImpl.NamespaceHandler {
    private const val SET_LAST_USER = "setLastUser"
    private const val GET_LAST_USER = "getLastUser"

    override val namespace: OwnIdWebViewBridge.Namespace = OwnIdWebViewBridge.Namespace.STORAGE
    override val actions: Array<String> = arrayOf(SET_LAST_USER, GET_LAST_USER)

    @UiThread
    override fun handle(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        bridgeContext.launch {
            try {
                if (SET_LAST_USER.equals(action, ignoreCase = true)) {
                    val paramsJSON = params?.let { JSONObject(it) } ?: run {
                        throw IllegalArgumentException("OwnIdWebViewBridgeStorage.invoke: No params set for '$action'")
                    }

                    val loginId = paramsJSON.getString("loginId")
                    val authMethod = AuthMethod.fromString(paramsJSON.optString("authMethod"))

                    bridgeContext.ownIdCore.repository.saveLoginId(loginId, authMethod)
                    bridgeContext.finishWithSuccess("{}")

                    return@launch
                }

                if (GET_LAST_USER.equals(action, ignoreCase = true)) {
                    val result = bridgeContext.ownIdCore.repository.getLoginId()?.let { loginId ->
                        val authMethod = bridgeContext.ownIdCore.repository.getLoginIdData(loginId).authMethod?.name?.lowercase()
                        JSONObject()
                            .put("loginId", loginId)
                            .put("authMethod", authMethod)
                            .toString()
                    } ?: "null"

                    bridgeContext.finishWithSuccess(result)

                    return@launch
                }

                throw IllegalArgumentException("OwnIdWebViewBridgeStorage: Unsupported action: '$action'")
            } catch (cause: CancellationException) {
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeStorage, cause)
                throw cause
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeStorage, "invoke: $action", cause.message, cause)
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeStorage, cause)
            }
        }
    }
}