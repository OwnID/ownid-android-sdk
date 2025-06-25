package com.ownid.sdk.internal.feature.nativeflow.steps.fido

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.createFidoLoginOptions
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.toJSONObject
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class FidoLoginAuthStep private constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    data: Data
) : AbstractFidoAuthStep(ownIdNativeFlowData, onNextStep, data) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true) && getOperationOrNull(stepJson) == Operation.LOGIN

        @Throws(OwnIdException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): AbstractStep =
            FidoLoginAuthStep(ownIdNativeFlowData, onNextStep, createData(stepJson))
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)

        if (data.credIds.isEmpty()) {
            OwnIdInternalLogger.logW(this, "run", "FIDO: Login but no credentials specified, trying to register new one")
            moveToNextStep(FidoRegisterAuthStep(ownIdNativeFlowData, onNextStep, data.copy(operation = Operation.REGISTER)))
            return
        }

        runCatching {
            ownIdNativeFlowData.canceller.setOnCancelListener(onCancelListener)

            val requestJson = createFidoLoginOptions(ownIdNativeFlowData.context, data.rpId, data.credIds)
            val request = GetCredentialRequest(
                listOf(GetPublicKeyCredentialOption(requestJson)), preferImmediatelyAvailableCredentials = true
            )
            CredentialManager.create(activity).getCredentialAsync(
                activity, request, canceller, ContextCompat.getMainExecutor(activity),
                object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                    override fun onError(e: GetCredentialException) = onFidoError(e)
                    override fun onResult(result: GetCredentialResponse) {
                        OwnIdInternalLogger.logD(this@FidoLoginAuthStep, "onLoginResult", "Invoked")
                        runCatching {
                            when (val credential = result.credential) {
                                is PublicKeyCredential -> credential.toJSONObject()
                                else -> throw OwnIdException("GetCredentialResponse unsupported result type: ${credential.type}")
                            }
                        }
                            .onSuccess { onFidoResult(it, false) }
                            .onFailure { onFidoError(it) }
                    }
                }
            )
        }.onFailure { onFidoError(it) }
    }

    @MainThread
    private fun onFidoError(error: Throwable) {
        OwnIdInternalLogger.logD(this, "onFidoError", error.message, error)

        val message = if (error is GetCredentialException) error.type else error.message
        OwnIdInternalLogger.logW(this, "onFidoError", "[RpID: ${data.rpId}] $message", error)
        sendMetric(Metric.EventType.Error, "FIDO: Execution Did Not Complete", errorMessage = message)

        // No Credential available, trying to Register instead of just fail.
        if (error is NoCredentialException) {
            OwnIdInternalLogger.logI(this, "onFidoError", "Login failed, trying to register new one")
            sendMetric(Metric.EventType.Track, "FIDO: Trying to register new one", errorMessage = message)
            moveToNextStep(FidoRegisterAuthStep(ownIdNativeFlowData, onNextStep, data.copy(operation = Operation.REGISTER, credIds = emptyList())))
            return
        }

        val result = JSONObject()
            .put("name", error::class.java.name)
            .put("type", if (error is GetCredentialException) error.type else "")
            .put("message", error.message ?: "Unknown")

        onFidoResult(result, true)
    }
}