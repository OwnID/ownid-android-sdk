package com.ownid.sdk.internal.feature.webbridge

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.createFidoLoginOptions
import com.ownid.sdk.internal.createFidoRegisterOptions
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toJSONObject
import org.json.JSONObject
import kotlin.random.Random

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeFido : OwnIdWebViewBridgeImpl.Namespace {
    private const val IS_AVAILABLE = "isAvailable"
    private const val CREATE = "create"
    private const val GET = "get"

    override val name: String = "FIDO"
    override val actions: Array<String> = arrayOf(IS_AVAILABLE, CREATE, GET)

    @UiThread
    override fun invoke(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        try {
            if (actions.none { it.equals(action, ignoreCase = true) })
                throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: Unsupported action: '$action'")

            when (action) {
                IS_AVAILABLE -> runOnMainThread { runIsAvailable(bridgeContext) }

                CREATE -> {
                    val paramsJSON = params?.let { JSONObject(it) } ?: run {
                        throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: No params set")
                    }

                    val fidoParams = FidoRegisterParams(
                        context = paramsJSON.optString("context")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'context' cannot be empty") },
                        rpId = paramsJSON.optString("relyingPartyId")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'relyingPartyId' cannot be empty") },
                        rpName = paramsJSON.optString("relyingPartyName")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'relyingPartyName' cannot be empty ") },
                        userId = Random.nextBytes(32).toBase64UrlSafeNoPadding(),
                        userName = paramsJSON.optString("userName")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'userName' cannot be empty") },
                        userDisplayName = paramsJSON.optString("userDisplayName")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'userDisplayName' cannot be empty") },
                        credIds = (paramsJSON.optJSONArray("credsIds")
                            ?.let { array -> List(array.length()) { array.optString(it) }.ifEmpty { null } }
                            ?: listOf(paramsJSON.optString("credId"))
                                ).filter { it.isNotBlank() }
                    )
                    runOnMainThread { runFidoRegister(bridgeContext, fidoParams) }
                }

                GET -> {
                    val paramsJSON = params?.let { JSONObject(it) } ?: run {
                        throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: No params set")
                    }

                    val fidoParams = FidoLoginParams(
                        context = paramsJSON.optString("context")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'context' cannot be empty") },
                        rpId = paramsJSON.optString("relyingPartyId")
                            .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'relyingPartyId' cannot be empty") },
                        credIds = (paramsJSON.optJSONArray("credsIds")
                            ?.let { array -> List(array.length()) { array.optString(it) }.ifEmpty { null } }
                            ?: listOf(paramsJSON.optString("credId"))
                                ).filter { it.isNotBlank() }
                    )
                    runOnMainThread { runFidoLogin(bridgeContext, fidoParams) }
                }
            }
        } catch (cause: Throwable) {
            OwnIdInternalLogger.logW(this, "invoke", cause.message, cause)

            val result = JSONObject()
                .put("name", "OwnIdWebViewBridgeFido")
                .put("type", cause::class.java.simpleName)
                .put("message", cause.message)
                .put("code", 0)

            runOnMainThread { bridgeContext.invokeErrorCallback(result) }
        }
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.getMainLooper().isCurrentThread) action.invoke()
        else Handler(Looper.getMainLooper()).post { action.invoke() }
    }

    private class FidoRegisterParams(
        val context: String, val rpId: String, val rpName: String, val userId: String, val userName: String, val userDisplayName: String, val credIds: List<String>
    )

    private class FidoLoginParams(val context: String, val rpId: String, val credIds: List<String>)

    @MainThread
    private fun runIsAvailable(bridgeContext: OwnIdWebViewBridgeContext) {
        if (bridgeContext.isCanceled()) {
            OwnIdInternalLogger.logI(this, "runIsAvailable", "Operation canceled")
            return
        }

        runCatching {
            bridgeContext.ensureMainFrame()
            bridgeContext.ensureOriginSecureScheme()

            val isAvailable = bridgeContext.ownIdCore.configuration.isFidoPossible()
            bridgeContext.invokeSuccessCallback(isAvailable.toString())
        }.onFailure { error ->
            OwnIdInternalLogger.logW(this, "runIsAvailable", error.message, error)

            val result = JSONObject()
                .put("name", error::class.java.name)
                .put("type", "")
                .put("message", error.message ?: "Unknown")
                .put("code", 0)

            bridgeContext.invokeErrorCallback(result)
        }
    }

    @MainThread
    private fun runFidoRegister(bridgeContext: OwnIdWebViewBridgeContext, params: FidoRegisterParams) {
        if (bridgeContext.isCanceled()) {
            OwnIdInternalLogger.logI(this, "runFidoRegister", "Operation canceled")
            return
        }
        OwnIdInternalLogger.logD(this, "runFidoRegister", "Invoked")

        runCatching {
            bridgeContext.ensureMainFrame()
            bridgeContext.ensureOriginSecureScheme()
            bridgeContext.ensureAllowedOrigin()

            val requestJson = createFidoRegisterOptions(
                params.context, params.rpId, params.rpName, params.userId, params.userName, params.userDisplayName, params.credIds
            )

            val request = CreatePublicKeyCredentialRequest(requestJson, preferImmediatelyAvailableCredentials = true)

            CredentialManager.create(bridgeContext.webView.context)
                .createCredentialAsync(bridgeContext.webView.context, request, bridgeContext.canceller, ContextCompat.getMainExecutor(bridgeContext.webView.context),
                    object : CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException> {
                        override fun onError(e: CreateCredentialException) = onFidoRegisterError(bridgeContext, e)
                        override fun onResult(result: CreateCredentialResponse) {
                            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "onRegisterResult", "Invoked")
                            runCatching {
                                if (result is CreatePublicKeyCredentialResponse) result.toJSONObject()
                                else throw OwnIdException("CreateCredentialResponse unsupported result type: ${result.type}")
                            }.onSuccess {
                                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runFidoRegister.onSuccess", "Invoked")
                                bridgeContext.invokeSuccessCallback(it.toString())
                            }.onFailure { onFidoRegisterError(bridgeContext, it) }
                        }
                    }
                )
        }.onFailure { onFidoRegisterError(bridgeContext, it) }
    }

    @MainThread
    private fun onFidoRegisterError(bridgeContext: OwnIdWebViewBridgeContext, error: Throwable) {
        OwnIdInternalLogger.logD(this, "onFidoRegisterError", error.message, error)

        val message = if (error is CreateCredentialException) error.type else error.message
        OwnIdInternalLogger.logW(this, "onFidoRegisterError", message, error)

        val name = if (error is CreatePublicKeyCredentialDomException) error.domError::class.java.simpleName else error::class.java.name
        val result = JSONObject()
            .put("name", name)
            .put("type", if (error is CreateCredentialException) error.type else "")
            .put("message", error.message ?: "Unknown")
            .put("code", 0)

        bridgeContext.invokeErrorCallback(result)
    }

    @MainThread
    private fun runFidoLogin(bridgeContext: OwnIdWebViewBridgeContext, params: FidoLoginParams) {
        if (bridgeContext.isCanceled()) {
            OwnIdInternalLogger.logI(this, "runFidoLogin", "Operation canceled")
            return
        }
        OwnIdInternalLogger.logD(this, "runFidoLogin", "Invoked")

        runCatching {
            bridgeContext.ensureMainFrame()
            bridgeContext.ensureOriginSecureScheme()
            bridgeContext.ensureAllowedOrigin()

            val requestJson = createFidoLoginOptions(params.context, params.rpId, params.credIds)

            val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson)), preferImmediatelyAvailableCredentials = true)

            CredentialManager.create(bridgeContext.webView.context)
                .getCredentialAsync(bridgeContext.webView.context, request, bridgeContext.canceller, ContextCompat.getMainExecutor(bridgeContext.webView.context),
                    object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                        override fun onError(e: GetCredentialException) = onFidoLoginError(bridgeContext, e)
                        override fun onResult(result: GetCredentialResponse) {
                            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "onLoginResult", "Invoked")
                            runCatching {
                                when (val credential = result.credential) {
                                    is PublicKeyCredential -> credential.toJSONObject()
                                    else -> throw OwnIdException("GetCredentialResponse unsupported result type: ${credential.type}")
                                }
                            }.onSuccess {
                                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runFidoLogin.onSuccess", "Invoked")
                                bridgeContext.invokeSuccessCallback(it.toString())
                            }.onFailure { onFidoLoginError(bridgeContext, it) }
                        }
                    }
                )
        }.onFailure { onFidoLoginError(bridgeContext, it) }
    }

    @MainThread
    private fun onFidoLoginError(bridgeContext: OwnIdWebViewBridgeContext, error: Throwable) {
        OwnIdInternalLogger.logD(this, "onFidoLoginError", error.message, error)

        val message = if (error is GetCredentialException) error.type else error.message
        OwnIdInternalLogger.logW(this, "onFidoLoginError", message, error)

        val name = if (error is GetPublicKeyCredentialDomException) error.domError::class.java.simpleName else error::class.java.name
        val result = JSONObject()
            .put("name", name)
            .put("type", if (error is GetCredentialException) error.type else "")
            .put("message", error.message ?: "Unknown")
            .put("code", 0)

        bridgeContext.invokeErrorCallback(result)
    }
}