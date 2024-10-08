package com.ownid.sdk.internal.feature.nativeflow.steps.idcollect

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
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowLoginId
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class IdCollectStep private constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val url: HttpUrl
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        private const val TYPE: String = "Starting"

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true)

        @Throws(IllegalArgumentException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): IdCollectStep {
            val stepData = stepJson.getJSONObject("data")
            val url = stepData.optString("url")
                .ifBlank { throw IllegalArgumentException("IdCollectStep.create: 'url' cannot be empty") }.toHttpUrl()
            return IdCollectStep(ownIdNativeFlowData, onNextStep, url)
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
        state.value = if (ownIdNativeFlowData.loginId is OwnIdNativeFlowLoginId.PhoneNumber) {
            val phoneCodeList = ownIdNativeFlowData.ownIdCore.configuration.server.phoneCodes
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
            if (error is OwnIdNativeFlowError) error.userMessage else error.message ?: error.toString(),
            error.message,
            when (error) {
                is OwnIdNativeFlowError -> error.errorCode
                is IdCollectStepWrongLoginId -> OwnIdNativeFlowError.CodeLocal.INVALID_LOGIN_ID
                else -> null
            }
        )
        updateState { copy(error = error) }
    }

    @MainThread
    internal fun onPhoneCodeSelected(index: Int) {
        OwnIdInternalLogger.logD(this, "onPhoneCodeSelected", "Invoked")
        val phoneCode = ownIdNativeFlowData.ownIdCore.configuration.server.phoneCodes[index]
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

        val newLoginId = when (ownIdNativeFlowData.loginId) {
            is OwnIdNativeFlowLoginId.Email -> loginId
            is OwnIdNativeFlowLoginId.PhoneNumber -> state.value!!.phoneCode!!.dialCode + loginId
            is OwnIdNativeFlowLoginId.UserName -> loginId //TODO
        }

        val ownIdNativeFlowLoginId = OwnIdNativeFlowLoginId.fromString(newLoginId, ownIdNativeFlowData.ownIdCore.configuration)
        ownIdNativeFlowData.loginId = ownIdNativeFlowLoginId
        ownIdNativeFlowData.useLoginId = true
        ownIdNativeFlowData.ownIdCore.eventsService.setFlowLoginId(ownIdNativeFlowLoginId.value)

        if (ownIdNativeFlowLoginId.isValid().not()) {
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
                .put("loginId", ownIdNativeFlowData.loginId.value)
                .put("supportsFido2", ownIdNativeFlowData.ownIdCore.configuration.isFidoPossible())
                .toString()

            doPostRequest(ownIdNativeFlowData, url, postData) {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdNativeFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}