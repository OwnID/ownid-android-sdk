package com.ownid.sdk.internal.feature.flow.steps.idcollect

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.config.OwnIdServerConfiguration
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.flow.AbstractStep
import com.ownid.sdk.internal.feature.flow.OwnIdFlowData
import com.ownid.sdk.internal.feature.flow.OwnIdFlowError
import com.ownid.sdk.internal.feature.flow.OwnIdFlowLoginId
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class IdCollectStep private constructor(
    ownIdFlowData: OwnIdFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val url: HttpUrl
) : AbstractStep(ownIdFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        private const val TYPE: String = "Starting"

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true)

        @Throws(IllegalArgumentException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData, onNextStep: (AbstractStep) -> Unit): IdCollectStep {
            val stepData = stepJson.getJSONObject("data")
            val url = stepData.optString("url")
                .ifBlank { throw IllegalArgumentException("IdCollectStep.create: 'url' cannot be empty") }.toHttpUrl()
            return IdCollectStep(ownIdFlowData, onNextStep, url)
        }
    }

    @InternalOwnIdAPI
    internal object IdCollectStepWrongLoginId : OwnIdException("User entered invalid Login ID")

    @InternalOwnIdAPI
    internal data class IdCollectState(
        val isBusy: Boolean = false,
        val phoneCode: OwnIdServerConfiguration.PhoneCode? = null,
        val error: OwnIdException? = null,
        val done: Boolean = false
    )

    @InternalOwnIdAPI
    internal val state = MutableLiveData<IdCollectState>()

    private inline fun updateState(crossinline block: IdCollectState.() -> IdCollectState) {
        state.value = block.invoke(state.value as IdCollectState)
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)
        state.value = if (ownIdFlowData.loginId is OwnIdFlowLoginId.PhoneNumber) {
            val phoneCodeList = ownIdFlowData.ownIdCore.configuration.server.phoneCodes
            val defaultPhoneCode = phoneCodeList.firstOrNull { it.code == "US" } ?: phoneCodeList.first()
            IdCollectState(phoneCode = defaultPhoneCode)
        } else IdCollectState()

        IdCollectStepUI.show(activity.supportFragmentManager)
    }

    override fun getMetricViewedAction(): String = "Viewed LoginId Completion"

    override fun getMetricSource(): String = "LoginId Completion"

    @MainThread
    private fun onError(error: OwnIdException) {
        OwnIdInternalLogger.logW(this, "onError", error.message, error)

        sendMetric(
            Metric.EventType.Error,
            if (error is OwnIdFlowError) error.userMessage else error.message ?: error.toString(),
            error.message,
            when (error) {
                is OwnIdFlowError -> error.errorCode
                is IdCollectStepWrongLoginId -> OwnIdFlowError.CodeLocal.INVALID_LOGIN_ID
                else -> null
            }
        )
        updateState { copy(error = error) }
    }

    @MainThread
    internal fun onPhoneCodeSelected(index: Int) {
        OwnIdInternalLogger.logD(this, "onPhoneCodeSelected", "Invoked")
        val phoneCode = ownIdFlowData.ownIdCore.configuration.server.phoneCodes[index]
        sendMetric(Metric.EventType.Track, "User selected country: $phoneCode")
        updateState { copy(phoneCode = phoneCode) }
    }

    @MainThread
    internal fun onTextChanged() {
        if (state.value?.error != null) updateState { copy(error = null) }
    }

    @MainThread
    internal fun onLoginId(loginId: String) {
        OwnIdInternalLogger.logD(this, "onLoginId", "Invoked")

        val newLoginId = when (ownIdFlowData.loginId) {
            is OwnIdFlowLoginId.Email -> loginId
            is OwnIdFlowLoginId.PhoneNumber -> state.value!!.phoneCode!!.dialCode + loginId
            is OwnIdFlowLoginId.UserName -> loginId //TODO
        }

        val ownIdFlowLoginId = OwnIdFlowLoginId.fromString(newLoginId, ownIdFlowData.ownIdCore.configuration)
        ownIdFlowData.loginId = ownIdFlowLoginId
        ownIdFlowData.useLoginId = true
        ownIdFlowData.ownIdCore.eventsService.setFlowLoginId(ownIdFlowLoginId.value)

        if (ownIdFlowLoginId.isValid().not()) {
            onError(IdCollectStepWrongLoginId)
            return
        }

        sendMetric(Metric.EventType.Track, "Clicked Continue")

        updateState { copy(isBusy = true, error = null) }

        doStepRequest {
            updateState { copy(isBusy = false) }
            mapCatching { nextStep ->
                updateState { copy(done = true) }
                moveToNextStep(nextStep)
            }.onFailure { onError(OwnIdException.map("IdCollectStep.doStepRequest: ${it.message}", it)) }
        }
    }

    @MainThread
    private fun doStepRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            val postData = JSONObject()
                .put("loginId", ownIdFlowData.loginId.value)
                .put("supportsFido2", ownIdFlowData.ownIdCore.configuration.isFidoPossible())
                .toString()

            doPostRequest(ownIdFlowData, url, postData) {
                if (ownIdFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}