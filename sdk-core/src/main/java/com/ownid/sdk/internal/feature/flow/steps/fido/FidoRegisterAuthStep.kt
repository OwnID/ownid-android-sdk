package com.ownid.sdk.internal.feature.flow.steps.fido

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.CreateCredentialException
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.createFidoRegisterOptions
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.flow.AbstractStep
import com.ownid.sdk.internal.feature.flow.OwnIdFlowData
import com.ownid.sdk.internal.toJSONObject
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class FidoRegisterAuthStep internal constructor(
    ownIdFlowData: OwnIdFlowData,
    onNextStep: (AbstractStep) -> Unit,
    data: Data
) : AbstractFidoAuthStep(ownIdFlowData, onNextStep, data) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true) && getOperationOrNull(stepJson) == Operation.REGISTER

        @Throws(OwnIdException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData, onNextStep: (AbstractStep) -> Unit): AbstractStep =
            FidoRegisterAuthStep(ownIdFlowData, onNextStep, createData(stepJson))
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)

        runCatching {
            ownIdFlowData.canceller.setOnCancelListener(onCancelListener)

            val requestJson = createFidoRegisterOptions(
                ownIdFlowData.context, data.rpId, data.rpName, data.userId, data.userName, data.userDisplayName, data.credIds
            )
            val request = CreatePublicKeyCredentialRequest(requestJson, clientDataHash = null, preferImmediatelyAvailableCredentials = true)
            CredentialManager.create(activity).createCredentialAsync(activity, request, canceller, ContextCompat.getMainExecutor(activity),
                object : CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException> {
                    override fun onError(e: CreateCredentialException) = onFidoError(e)
                    override fun onResult(result: CreateCredentialResponse) {
                        OwnIdInternalLogger.logD(this@FidoRegisterAuthStep, "onRegisterResult", "Invoked")
                        runCatching {
                            if (result is CreatePublicKeyCredentialResponse) result.toJSONObject()
                            else throw OwnIdException("CreateCredentialResponse unsupported result type: ${result.type}")
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

        val message = if (error is CreateCredentialException) error.type else error.message
        OwnIdInternalLogger.logW(this, "onFidoError", "[RpID: ${data.rpId}] $message", error)
        sendMetric(Metric.EventType.Error, "FIDO: Execution Did Not Complete", errorMessage = message)

        val result = JSONObject()
            .put("name", error::class.java.name)
            .put("type", if (error is CreateCredentialException) error.type else "")
            .put("message", error.message ?: "Unknown")

        onFidoResult(result, true)
    }
}