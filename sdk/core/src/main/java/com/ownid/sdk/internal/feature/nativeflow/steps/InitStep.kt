package com.ownid.sdk.internal.feature.nativeflow.steps

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.fromBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toSHA256Bytes
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class InitStep private constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val url: HttpUrl
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal companion object {
        internal fun create(ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): InitStep {
            val url = ownIdNativeFlowData.ownIdCore.configuration.server.serverUrl.newBuilder().addEncodedPathSegments("mobile/v1/ownid").build()
            return InitStep(ownIdNativeFlowData, onNextStep, url)
        }
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)
        OwnIdInternalLogger.logI(this, "run", "isFidoPossible: ${ownIdNativeFlowData.ownIdCore.configuration.isFidoPossible()}")

        doStepRequest {
            mapCatching { nextStep ->
                OwnIdInternalLogger.setFlowContext(ownIdNativeFlowData.context)
                ownIdNativeFlowData.ownIdCore.eventsService.setFlowContext(ownIdNativeFlowData.context)
                moveToNextStep(nextStep)
            }.onFailure {
                val error = OwnIdException.map("InitStep.doStepRequest: ${it.message}", it)
                moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.failure(error)))
            }
        }
    }

    @MainThread
    private fun doStepRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            val postData = JSONObject()
                .put("type", ownIdNativeFlowData.flowType.name.lowercase())
                .apply {
                    if (ownIdNativeFlowData.loginId.isNotEmpty() && ownIdNativeFlowData.useLoginId)
                        put("loginId", ownIdNativeFlowData.loginId.value)
                }
                .apply {
                    if (ownIdNativeFlowData.loginType != null)
                        put("loginType", ownIdNativeFlowData.loginType.name.replaceFirstChar { it.lowercase() })
                }
                .put("supportsFido2", ownIdNativeFlowData.ownIdCore.configuration.isFidoPossible())
                .put("passkeyAutofill", ownIdNativeFlowData.passkeyAutofill)
                .put("qr", ownIdNativeFlowData.qr)
                .put("sessionChallenge", ownIdNativeFlowData.verifier.fromBase64UrlSafeNoPadding().toSHA256Bytes().toBase64UrlSafeNoPadding())
                .toString()

            doPostRequest(ownIdNativeFlowData, url, postData) {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching {
                    val jsonResponse = JSONObject(it)

                    ownIdNativeFlowData.context = jsonResponse.optString("context")
                        .ifBlank { throw IllegalArgumentException("'context' cannot be empty") }

                    ownIdNativeFlowData.expiration = jsonResponse.optLong("expiration", 1200000L)
                        .let { expiration -> if (expiration <= 0) 1200000L else expiration }

                    ownIdNativeFlowData.stopUrl = jsonResponse.optString("stopUrl")
                        .ifBlank { throw IllegalArgumentException("'stopUrl' cannot be empty") }
                        .toHttpUrl()

                    ownIdNativeFlowData.statusFinalUrl = jsonResponse.optString("finalStatusUrl")
                        .ifBlank { throw IllegalArgumentException("'finalStatusUrl' cannot be empty") }
                        .toHttpUrl()

                    parseResponse(jsonResponse, ownIdNativeFlowData, onNextStep)
                }
                    .callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}