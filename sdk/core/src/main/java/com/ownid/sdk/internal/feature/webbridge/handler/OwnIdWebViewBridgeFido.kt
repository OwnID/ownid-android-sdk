package com.ownid.sdk.internal.feature.webbridge.handler

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.adjustEnrollmentOptions
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.createFidoLoginOptions
import com.ownid.sdk.internal.createFidoRegisterOptions
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toJSONObject
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeFido : OwnIdWebViewBridgeImpl.NamespaceHandler {
    private const val IS_AVAILABLE = "isAvailable"
    private const val CREATE = "create"
    private const val GET = "get"

    override val namespace: OwnIdWebViewBridge.Namespace = OwnIdWebViewBridge.Namespace.FIDO
    override val actions: Array<String> = arrayOf(IS_AVAILABLE, CREATE, GET)

    @UiThread
    override fun handle(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        bridgeContext.launch {
            try {
                if (IS_AVAILABLE.equals(action, ignoreCase = true)) {
                    bridgeContext.runIsAvailable()

                    return@launch
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
                                    ).filter { it.isNotBlank() }.toSet().toList()
                        )

                        bridgeContext.runFidoRegister(options, isOwnIdFlow = true)
                    } else {
                        val options = adjustEnrollmentOptions(params)
                        bridgeContext.runFidoRegister(options, isOwnIdFlow = false)
                    }

                    return@launch
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
                                ).filter { it.isNotBlank() }.toSet().toList()
                    )
                    bridgeContext.runFidoLogin(options)

                    return@launch
                }

                throw IllegalArgumentException("OwnIdWebViewBridgeFido: Unsupported action: '$action'")
            } catch (cause: CancellationException) {
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeFido, cause)
                throw cause
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this, "invoke: $action", cause.message, cause)
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeFido, cause)
            }
        }
    }

    @MainThread
    private suspend fun OwnIdWebViewBridgeContext.runIsAvailable() {
        OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runIsAvailable", "Invoked")

        ensureMainFrame()
        ensureAllowedOrigin()

        val isAvailable = ownIdCore.configuration.isFidoPossible()
        finishWithSuccess(isAvailable.toString())
    }

    @MainThread
    @SuppressLint("PublicKeyCredential")
    private suspend fun OwnIdWebViewBridgeContext.runFidoRegister(createOptions: String, isOwnIdFlow: Boolean) {
        try {
            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runFidoRegister", "Invoked")

            ensureMainFrame()
            ensureAllowedOrigin()

            val request = CreatePublicKeyCredentialRequest(createOptions, preferImmediatelyAvailableCredentials = true)
            val result = CredentialManager.create(webView.context).createCredential(webView.context, request)

            if (result is CreatePublicKeyCredentialResponse) {
                val result2 = if (isOwnIdFlow) result.toJSONObject().toString() else result.registrationResponseJson
                finishWithSuccess(result2)
            } else {
                throw OwnIdException("CreateCredentialResponse unsupported result type: ${result.type}")
            }
        } catch (cause: CancellationException) {
            finishWithError(this@OwnIdWebViewBridgeFido, cause)
            throw cause
        } catch (cause: Throwable) {
            val message = when (cause) {
                is CreatePublicKeyCredentialDomException -> cause.domError.type
                is CreateCredentialException -> cause.type
                else -> cause.message
            }
            OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeFido, "onFidoRegisterError", message, cause)

            finishWithError(this@OwnIdWebViewBridgeFido, cause)
        }
    }

    @MainThread
    private suspend fun OwnIdWebViewBridgeContext.runFidoLogin(getOptions: String) {
        try {
            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeFido, "runFidoLogin", "Invoked")

            ensureMainFrame()
            ensureAllowedOrigin()

            val request = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(getOptions)), preferImmediatelyAvailableCredentials = true
            )
            val result = CredentialManager.create(webView.context).getCredential(webView.context, request)

            when (val credential = result.credential) {
                is PublicKeyCredential -> finishWithSuccess(credential.toJSONObject().toString())
                else -> throw OwnIdException("GetCredentialResponse unsupported result type: ${credential.type}")
            }
        } catch (cause: CancellationException) {
            finishWithError(this@OwnIdWebViewBridgeFido, cause)
            throw cause
        } catch (cause: Throwable) {
            val message = when (cause) {
                is GetPublicKeyCredentialDomException -> cause.domError.type
                is GetCredentialException -> cause.type
                else -> cause.message
            }
            OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeFido, "onFidoLoginError", message, cause)

            val type = if (cause is NoCredentialException) "TYPE_NO_CREDENTIAL" else null
            finishWithError(this@OwnIdWebViewBridgeFido, cause, type)
        }
    }
}