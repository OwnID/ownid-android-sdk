package com.ownid.sdk.internal.feature.flow.steps

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.feature.flow.AbstractStep
import com.ownid.sdk.internal.feature.flow.OwnIdFlowData
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class SuccessStep private constructor(
    ownIdFlowData: OwnIdFlowData,
    onNextStep: (AbstractStep) -> Unit
) : AbstractStep(ownIdFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        private const val TYPE: String = "success"

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true)

        override fun create(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData, onNextStep: (AbstractStep) -> Unit): SuccessStep =
            SuccessStep(ownIdFlowData, onNextStep)
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)

        doStepRequest {
            mapCatching {
                moveToNextStep(DoneStep(ownIdFlowData, onNextStep, Result.success(it)))
            }.onFailure {
                val error = OwnIdException.map("SuccessStep.doStepRequest: ${it.message}", it)
                moveToNextStep(DoneStep(ownIdFlowData, onNextStep, Result.failure(error)))
            }
        }
    }

    @MainThread
    private fun doStepRequest(callback: OwnIdCallback<OwnIdResponse>) {
        runCatching {
            val postJsonData = JSONObject().put("sessionVerifier", ownIdFlowData.verifier).toString()

            doPostRequest(ownIdFlowData, ownIdFlowData.statusFinalUrl, postJsonData) {
                if (ownIdFlowData.canceller.isCanceled) return@doPostRequest
                val languageTag = ownIdFlowData.ownIdCore.localeService.currentOwnIdLocale.serverLanguageTag
                mapCatching { OwnIdResponse.fromServerResponse(it, languageTag) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}