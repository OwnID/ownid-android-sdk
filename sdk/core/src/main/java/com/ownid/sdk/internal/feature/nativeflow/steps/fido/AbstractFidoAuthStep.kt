package com.ownid.sdk.internal.feature.nativeflow.steps.fido

import android.os.CancellationSignal
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.feature.nativeflow.steps.DoneStep
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal abstract class AbstractFidoAuthStep protected constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    protected val data: Data
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

    @InternalOwnIdAPI
    protected companion object {
        @JvmStatic
        protected val TYPE: String = "fido2Authorize"

        @JvmStatic
        @Throws(IllegalArgumentException::class, JSONException::class)
        protected fun createData(stepJson: JSONObject): Data {
            val stepData = stepJson.getJSONObject("data")

            return Data(
                operation = when (val type = stepData.optString("operation").lowercase()) {
                    "login" -> Operation.LOGIN
                    "register" -> Operation.REGISTER
                    else -> throw IllegalArgumentException("FidoAuthStep.create: 'operation' unknown: '$type'")
                },
                url = stepData.optString("url")
                    .ifBlank { throw IllegalArgumentException("FidoAuthStep.create: 'url' cannot be empty") }.toHttpUrl(),
                rpId = stepData.optString("relyingPartyId")
                    .ifBlank { throw IllegalArgumentException("FidoAuthStep.create: 'relyingPartyId' cannot be empty") },
                rpName = stepData.optString("relyingPartyName")
                    .ifBlank { throw IllegalArgumentException("FidoAuthStep.create: 'relyingPartyName' cannot be empty ") },
                userId = Random.nextBytes(32).toBase64UrlSafeNoPadding(),
                userName = stepData.optString("userName")
                    .ifBlank { throw IllegalArgumentException("FidoAuthStep.create: 'userName' cannot be empty") },
                userDisplayName = stepData.optString("userDisplayName")
                    .ifBlank { throw IllegalArgumentException("FidoAuthStep.create: 'userDisplayName' cannot be empty") },
                credIds = getCredIds(stepData)
            )
        }

        @JvmStatic
        private fun getCredIds(stepData: JSONObject): List<String> =
            stepData.optJSONArray("credsIds")
                ?.let { a -> List(a.length()) { a.optString(it) } }
                ?.filter { it.isNotBlank() }
                ?.ifEmpty { null }
                ?: listOf(stepData.optString("credId")).filter { it.isNotBlank() }

        @JvmStatic
        protected fun getOperationOrNull(stepJson: JSONObject): Operation? = runCatching {
            when (val type = stepJson.getJSONObject("data").optString("operation").lowercase()) {
                "login" -> Operation.LOGIN
                "register" -> Operation.REGISTER
                else -> throw IllegalArgumentException("FidoAuthStep.create: 'operation' unknown: '$type'")
            }
        }.onFailure {
            OwnIdInternalLogger.logW(this, "AbstractFidoAuthStep.getOperationOrNull", it.message, it)
        }.getOrNull()
    }

    @InternalOwnIdAPI
    internal data class Data(
        val operation: Operation, val url: HttpUrl, val rpId: String, val rpName: String,
        val userId: String, val userName: String, val userDisplayName: String, val credIds: List<String>,
    )

    @InternalOwnIdAPI
    internal enum class Operation { REGISTER, LOGIN }

    protected val canceller = CancellationSignal()
    protected val onCancelListener = androidx.core.os.CancellationSignal.OnCancelListener { canceller.cancel() }

    override fun run(activity: FragmentActivity) {
        super.run(activity)

        sendMetric(Metric.EventType.Track, "FIDO: About To Execute")
        OwnIdInternalLogger.logD(this@AbstractFidoAuthStep, "run", "FIDO ${data.operation}")
    }

    @MainThread
    protected fun onFidoResult(result: JSONObject, isError: Boolean) {
        OwnIdInternalLogger.logD(this, "onFidoResult", "Invoked")

        if (isError.not()) sendMetric(Metric.EventType.Track, "FIDO: Execution Completed Successfully")

        ownIdNativeFlowData.canceller.setOnCancelListener(null)

        val resultKey = if (isError) "error" else "result"

        doStepRequest(resultKey, result) {
            mapCatching { nextStep ->
                moveToNextStep(nextStep)
            }.onFailure {
                val error = OwnIdException.map("FidoAuthStep.onFidoResult: ${it.message}", it)
                moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.failure(error)))
            }
        }
    }

    @MainThread
    protected fun doStepRequest(resultKey: String, result: JSONObject, callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            val postData = JSONObject().put("type", data.operation.name.lowercase()).put(resultKey, result).toString()

            doPostRequest(ownIdNativeFlowData, data.url, postData) {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdNativeFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}