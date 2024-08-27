package com.ownid.sdk.internal.feature.nativeflow.steps

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class SuccessStep private constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        private const val TYPE: String = "success"

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true)

        override fun create(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): SuccessStep =
            SuccessStep(ownIdNativeFlowData, onNextStep)
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)

        doStepRequest {
            mapCatching {
                moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.success(it)))
            }.onFailure {
                val error = OwnIdException.map("SuccessStep.doStepRequest: ${it.message}", it)
                moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.failure(error)))
            }
        }
    }

    @MainThread
    private fun doStepRequest(callback: OwnIdCallback<OwnIdResponse>) {
        runCatching {
            val postJsonData = JSONObject().put("sessionVerifier", ownIdNativeFlowData.verifier).toString()

            doPostRequest(ownIdNativeFlowData, ownIdNativeFlowData.statusFinalUrl, postJsonData) {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                val languageTag = ownIdNativeFlowData.ownIdCore.localeService.currentOwnIdLocale.serverLanguageTag
                mapCatching { OwnIdResponse.fromServerResponse(it, languageTag) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}