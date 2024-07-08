package com.ownid.sdk.internal.feature.webbridge

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
import com.ownid.sdk.internal.adjustEnrollmentOptions
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
        if (bridgeContext.isCanceled()) {
            OwnIdInternalLogger.logI(this, "invoke: $action", "Operation canceled")
            return
        }

        try {
            if (IS_AVAILABLE.equals(action, ignoreCase = true)) {
                bridgeContext.launchOnMainThread { runIsAvailable() }
                return
            }

            if (CREATE.equals(action, ignoreCase = true)) {
                val paramsJSON = params?.let { JSONObject(it) } ?: run {
                    throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: No params set")
                }

                if (paramsJSON.has("context")) {
                    val options = createFidoRegisterOptions(
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
                    bridgeContext.launchOnMainThread { runFidoRegister(options, isOwnIdFlow = true) }
                } else {
                    val options = adjustEnrollmentOptions(params)
                    bridgeContext.launchOnMainThread { runFidoRegister(options, isOwnIdFlow = false) }
                }
                return
            }

            if (GET.equals(action, ignoreCase = true)) {
                val paramsJSON = params?.let { JSONObject(it) } ?: run {
                    throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: No params set")
                }

                val options = createFidoLoginOptions(
                    context = paramsJSON.optString("context")
                        .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'context' cannot be empty") },
                    rpId = paramsJSON.optString("relyingPartyId")
                        .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: 'relyingPartyId' cannot be empty") },
                    credIds = (paramsJSON.optJSONArray("credsIds")
                        ?.let { array -> List(array.length()) { array.optString(it) }.ifEmpty { null } }
                        ?: listOf(paramsJSON.optString("credId"))
                            ).filter { it.isNotBlank() }
                )
                bridgeContext.launchOnMainThread { runFidoLogin(options) }
                return
            }

            throw IllegalArgumentException("OwnIdWebViewBridgeFido.invoke: Unsupported action: '$action'")
        } catch (cause: Throwable) {
            OwnIdInternalLogger.logW(this, "invoke: $action", cause.message, cause)

            val result = JSONObject()
                .put("name", "OwnIdWebViewBridgeFido")
                .put("type", cause::class.java.simpleName)
                .put("message", cause.message)
                .put("code", 0)

            bridgeContext.launchOnMainThread { invokeErrorCallback(result) }
        }
    }

    @MainThread
    private fun OwnIdWebViewBridgeContext.runIsAvailable() {
        runCatching {
            OwnIdInternalLogger.logD(this, "runIsAvailable", "Invoked")

            ensureMainFrame()
            ensureOriginSecureScheme()

            val isAvailable = ownIdCore.configuration.isFidoPossible()
            invokeSuccessCallback(isAvailable.toString())
        }.onFailure { error ->
            OwnIdInternalLogger.logW(this, "runIsAvailable", error.message, error)

            val result = JSONObject()
                .put("name", error::class.java.name)
                .put("type", "")
                .put("message", error.message ?: "Unknown")
                .put("code", 0)

            invokeErrorCallback(result)
        }
    }

    @MainThread
    private fun OwnIdWebViewBridgeContext.runFidoRegister(registerOptions: String, isOwnIdFlow: Boolean) {
        runCatching {
            OwnIdInternalLogger.logD(this, "runFidoRegister", "Invoked")

            ensureMainFrame()
            ensureOriginSecureScheme()
            ensureAllowedOrigin()

            val request = CreatePublicKeyCredentialRequest(requestJson = registerOptions, preferImmediatelyAvailableCredentials = true)

            CredentialManager.create(webView.context)
                .createCredentialAsync(webView.context, request, canceller, ContextCompat.getMainExecutor(webView.context),
                    object : CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException> {
                        override fun onError(e: CreateCredentialException) = onFidoRegisterError(e)
                        override fun onResult(result: CreateCredentialResponse) {
                            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "onRegisterResult", "Invoked")
                            runCatching {
                                if (result is CreatePublicKeyCredentialResponse) {
                                    if (isOwnIdFlow) result.toJSONObject().toString()
                                    else result.registrationResponseJson
                                } else {
                                    throw OwnIdException("CreateCredentialResponse unsupported result type: ${result.type}")
                                }
                            }.onSuccess {
                                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runFidoRegister.onSuccess", "Invoked")
                                invokeSuccessCallback(it)
                            }.onFailure { onFidoRegisterError(it) }
                        }
                    }
                )
        }.onFailure { onFidoRegisterError(it) }
    }

    @MainThread
    private fun OwnIdWebViewBridgeContext.onFidoRegisterError(error: Throwable) {
        OwnIdInternalLogger.logD(this, "onFidoRegisterError", error.message, error)

        val message = if (error is CreateCredentialException) error.type else error.message
        OwnIdInternalLogger.logW(this, "onFidoRegisterError", message, error)

        val name = if (error is CreatePublicKeyCredentialDomException) error.domError::class.java.simpleName else error::class.java.name
        val result = JSONObject()
            .put("name", name)
            .put("type", if (error is CreateCredentialException) error.type else "")
            .put("message", error.message ?: "Unknown")
            .put("code", 0)

        invokeErrorCallback(result)
    }

    @MainThread
    private fun OwnIdWebViewBridgeContext.runFidoLogin(loginOptions: String) {
        runCatching {
            OwnIdInternalLogger.logD(this, "runFidoLogin", "Invoked")

            ensureMainFrame()
            ensureOriginSecureScheme()
            ensureAllowedOrigin()

            val request = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(requestJson = loginOptions)), preferImmediatelyAvailableCredentials = true
            )

            CredentialManager.create(webView.context)
                .getCredentialAsync(webView.context, request, canceller, ContextCompat.getMainExecutor(webView.context),
                    object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                        override fun onError(e: GetCredentialException) = onFidoLoginError(e)
                        override fun onResult(result: GetCredentialResponse) {
                            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "onLoginResult", "Invoked")
                            runCatching {
                                when (val credential = result.credential) {
                                    is PublicKeyCredential -> credential.toJSONObject()
                                    else -> throw OwnIdException("GetCredentialResponse unsupported result type: ${credential.type}")
                                }
                            }.onSuccess {
                                OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runFidoLogin.onSuccess", "Invoked")
                                invokeSuccessCallback(it.toString())
                            }.onFailure { onFidoLoginError(it) }
                        }
                    }
                )
        }.onFailure { onFidoLoginError(it) }
    }

    @MainThread
    private fun OwnIdWebViewBridgeContext.onFidoLoginError(error: Throwable) {
        OwnIdInternalLogger.logD(this, "onFidoLoginError", error.message, error)

        val message = if (error is GetCredentialException) error.type else error.message
        OwnIdInternalLogger.logW(this, "onFidoLoginError", message, error)

        val name = if (error is GetPublicKeyCredentialDomException) error.domError::class.java.simpleName else error::class.java.name
        val result = JSONObject()
            .put("name", name)
            .put("type", if (error is GetCredentialException) error.type else "")
            .put("message", error.message ?: "Unknown")
            .put("code", 0)

        invokeErrorCallback(result)
    }
}