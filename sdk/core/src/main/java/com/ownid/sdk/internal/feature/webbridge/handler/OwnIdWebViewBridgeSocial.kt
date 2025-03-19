package com.ownid.sdk.internal.feature.webbridge.handler

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.UiThread
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdWebViewBridge
import com.ownid.sdk.exception.OwnIdCancellationException
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeContext
import com.ownid.sdk.internal.feature.webbridge.OwnIdWebViewBridgeImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object OwnIdWebViewBridgeSocial : OwnIdWebViewBridgeImpl.NamespaceHandler {
    private const val GOOGLE = "Google"

    override val namespace: OwnIdWebViewBridge.Namespace = OwnIdWebViewBridge.Namespace.SOCIAL
    override val actions: Array<String> = arrayOf(GOOGLE)

    @UiThread
    override fun handle(bridgeContext: OwnIdWebViewBridgeContext, action: String?, params: String?) {
        bridgeContext.launch {
            try {
                if (GOOGLE.equals(action, ignoreCase = true)) {
                    val paramsJSON = params?.let { JSONObject(it) } ?: run {
                        throw IllegalArgumentException("OwnIdWebViewBridgeSocial.invoke: No params set")
                    }

                    val challengeId = paramsJSON.optString("challengeId")
                        .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeSocial.invoke: 'challengeId' cannot be empty") }

                    val clientId = paramsJSON.optString("clientId")
                        .ifBlank { throw IllegalArgumentException("OwnIdWebViewBridgeSocial.invoke: 'clientId' cannot be empty") }

                    bridgeContext.runGoogleSocialLogin(serverClientId = clientId, nonce = challengeId)
                    return@launch
                }

                throw IllegalArgumentException("OwnIdWebViewBridgeSocial: Unsupported action: '$action'")
            } catch (cause: CancellationException) {
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeSocial, OwnIdCancellationException("Sign in canceled", cause))
                throw cause
            } catch (cause: Throwable) {
                OwnIdInternalLogger.logW(this, "invoke: $action", cause.message, cause)
                bridgeContext.finishWithError(this@OwnIdWebViewBridgeSocial, cause)
            }
        }
    }

    @MainThread
    @SuppressLint("PublicKeyCredential")
    private suspend fun OwnIdWebViewBridgeContext.runGoogleSocialLogin(serverClientId: String, nonce: String?) {
        try {
            OwnIdInternalLogger.logD(this@OwnIdWebViewBridgeSocial, "runGoogleSocialLogin", "Invoked")

            ensureMainFrame()
            ensureAllowedOrigin()

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = CredentialManager.create(webView.context).getCredential(webView.context, request)

            when (val credential = result.credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                        finishWithSuccess("'$idToken'")
                    } else {
                        throw OwnIdException("Unsupported credential type: ${credential.type}")
                    }
                }

                else -> throw OwnIdException("Unsupported credential: ${credential::class.java.name}")
            }
        } catch (cause: Throwable) {
            when (cause) {
                is GetCredentialCancellationException,
                is CancellationException ->
                    finishWithError(this@OwnIdWebViewBridgeSocial, OwnIdCancellationException("Sign in canceled", cause))

                else -> {
                    OwnIdInternalLogger.logW(this@OwnIdWebViewBridgeSocial, "runGoogleSocialLogin", cause.message, cause)
                    finishWithError(this@OwnIdWebViewBridgeSocial, cause)
                }
            }
        }
    }
}