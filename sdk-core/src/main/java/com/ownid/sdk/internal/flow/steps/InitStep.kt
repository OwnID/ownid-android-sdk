package com.ownid.sdk.internal.flow.steps

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.flow.AbstractStep
import com.ownid.sdk.internal.flow.OwnIdFlowData
import com.ownid.sdk.internal.fromBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toSHA256Bytes
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class InitStep private constructor(
    ownIdFlowData: OwnIdFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val url: HttpUrl
) : AbstractStep(ownIdFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(ownIdFlowData: OwnIdFlowData, onNextStep: (AbstractStep) -> Unit): InitStep {
            val url = ownIdFlowData.ownIdCore.configuration.server.serverUrl.newBuilder().addEncodedPathSegments("mobile/v1/ownid").build()
            return InitStep(ownIdFlowData, onNextStep, url)
        }
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)
        OwnIdInternalLogger.logI(this, "run", "isFidoPossible: ${ownIdFlowData.ownIdCore.configuration.isFidoPossible()}")

        doStepRequest {
            mapCatching { nextStep ->
                OwnIdInternalLogger.setFlowContext(ownIdFlowData.context)
                ownIdFlowData.ownIdCore.eventsService.setFlowContext(ownIdFlowData.context)
                moveToNextStep(nextStep)
            }.onFailure {
                val error = OwnIdException.map("InitStep.doStepRequest: ${it.message}", it)
                moveToNextStep(DoneStep(ownIdFlowData, onNextStep, Result.failure(error)))
            }
        }
    }

    @MainThread
    private fun doStepRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            val postData = JSONObject()
                .put("type", ownIdFlowData.flowType.name.lowercase())
                .apply {
                    if (ownIdFlowData.loginId.isNotEmpty() && ownIdFlowData.useLoginId)
                        put("loginId", ownIdFlowData.loginId.value)
                }
                .put("supportsFido2", ownIdFlowData.ownIdCore.configuration.isFidoPossible())
                .put("passkeyAutofill", ownIdFlowData.passkeyAutofill)
                .put("qr", ownIdFlowData.qr)
                .put("sessionChallenge", ownIdFlowData.verifier.fromBase64UrlSafeNoPadding().toSHA256Bytes().toBase64UrlSafeNoPadding())
                .toString()

            doPostRequest(ownIdFlowData, url, postData) {
                if (ownIdFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching {
                    val jsonResponse = JSONObject(it)

                    ownIdFlowData.context = jsonResponse.optString("context")
                        .ifBlank { throw IllegalArgumentException("'context' cannot be empty") }

                    ownIdFlowData.expiration = jsonResponse.optLong("expiration", 1200000L)
                        .let { expiration -> if (expiration <= 0) 1200000L else expiration }

                    ownIdFlowData.stopUrl = jsonResponse.optString("stopUrl")
                        .ifBlank { throw IllegalArgumentException("'stopUrl' cannot be empty") }
                        .toHttpUrl()

                    ownIdFlowData.statusFinalUrl = jsonResponse.optString("finalStatusUrl")
                        .ifBlank { throw IllegalArgumentException("'finalStatusUrl' cannot be empty") }
                        .toHttpUrl()

                    parseResponse(jsonResponse, ownIdFlowData, onNextStep)
                }
                    .callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}