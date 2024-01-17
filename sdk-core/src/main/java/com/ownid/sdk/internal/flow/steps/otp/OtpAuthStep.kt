package com.ownid.sdk.internal.flow.steps.otp

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.exception.OwnIdUserError
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.events.Metric
import com.ownid.sdk.internal.flow.AbstractStep
import com.ownid.sdk.internal.flow.OwnIdFlowData
import com.ownid.sdk.internal.flow.OwnIdFlowError
import com.ownid.sdk.internal.flow.steps.InitStep
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OtpAuthStep private constructor(
    ownIdFlowData: OwnIdFlowData,
    onNextStep: (AbstractStep) -> Unit,
    internal val data: Data
) : AbstractStep(ownIdFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal enum class VerificationType { Email, Sms }

    @InternalOwnIdAPI
    internal enum class OperationType(internal val metricName: String) { Sign("Fallback OTP Code"), Verify("OTP Code Verification") }

    @InternalOwnIdAPI
    internal class Data(
        val url: HttpUrl,
        val restartUrl: HttpUrl,
        val resendUrl: HttpUrl,
        val otpLength: Int,
        val verificationType: VerificationType,
        val operationType: OperationType
    )

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        private val TYPES: List<String> = listOf("linkWithCode", "loginIDAuthorization", "verifyLoginID")

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData): Boolean {
            val type = stepJson.optString("type")
            return TYPES.any { it.equals(type, ignoreCase = true) }
        }

        @Throws(IllegalArgumentException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdFlowData: OwnIdFlowData, onNextStep: (AbstractStep) -> Unit): OtpAuthStep {
            val stepData = stepJson.getJSONObject("data")

            val data = Data(
                stepData.optString("url")
                    .ifBlank { throw IllegalArgumentException("OtpAuthStep.create: 'url' cannot be empty") }.toHttpUrl(),
                stepData.optString("restartUrl")
                    .ifBlank { throw IllegalArgumentException("OtpAuthStep.create: 'restartUrl' cannot be empty") }.toHttpUrl(),
                stepData.optString("resendUrl")
                    .ifBlank { throw IllegalArgumentException("OtpAuthStep.create: 'resendUrl' cannot be empty") }.toHttpUrl(),
                stepData.optInt("otpLength", 4),
                stepData.getString("verificationType").let { type ->
                    VerificationType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) }
                        ?: throw IllegalArgumentException("OtpAuthStep.create: Unknown 'verificationType': $type")
                },
                if (stepJson.optString("type").equals("loginIDAuthorization", ignoreCase = true)) OperationType.Sign
                else OperationType.Verify
            )

            return OtpAuthStep(ownIdFlowData, onNextStep, data)
        }
    }

    @InternalOwnIdAPI
    internal data class OtpAuthState(
        val isBusy: Boolean = false,
        val resendVisible: Boolean = false,
        val otp: String = "",
        val error: OwnIdException? = null,
        val done: Boolean = false,
    )

    @InternalOwnIdAPI
    internal val state = MutableLiveData<OtpAuthState>()

    private inline fun updateState(crossinline block: OtpAuthState.() -> OtpAuthState) {
        state.value = block.invoke(state.value as OtpAuthState)
    }

    private val resendButtonRunnable = object : Runnable {
        override fun run() {
            OwnIdInternalLogger.logD(this, "onResendTimeOut", "Invoked")
            updateState { copy(resendVisible = true) }
        }
    }

    @MainThread
    override fun run(activity: FragmentActivity) {
        super.run(activity)
        state.value = OtpAuthState()
        OtpAuthStepUI.show(activity.supportFragmentManager)
        mainHandler.postDelayed(resendButtonRunnable, 15_000)
    }

    override fun moveToNextStep(nextStep: AbstractStep) {
        mainHandler.removeCallbacks(resendButtonRunnable)
        updateState { copy(done = true) }
        super.moveToNextStep(nextStep)
    }

    override fun getMetricViewedAction(): String = "Viewed ${data.operationType.metricName}"

    @MainThread
    private fun onError(error: OwnIdException) {
        if (error !is OwnIdFlowError) OwnIdInternalLogger.logE(this, "onError", error.message, error)

        val action = if (error is OwnIdFlowError && error.toOwnIdUserError("").code == OwnIdUserError.Code.WRONG_CODE)
            "[${data.operationType.metricName}] - Entered Wrong Verification Code"
        else
            "Viewed Error"

        sendMetric(Metric.EventType.Error, action, if (error is OwnIdFlowError) error.userMessage else error.message)

        updateState { copy(otp = "", error = error) }
    }

    @MainThread
    internal fun onOtp(otp: String) {
        OwnIdInternalLogger.logD(this, "onOtp", "Invoked")
        updateState { copy(otp = otp, error = null) }
        if (data.otpLength == otp.length) checkOtp(otp)
    }

    @MainThread
    private fun checkOtp(otp: String) {
        OwnIdInternalLogger.logD(this, "checkOtp", "Invoked")

        updateState { copy(isBusy = true, error = null) }

        doOtpAuthRequest(otp) {
            updateState { copy(isBusy = false) }
            mapCatching { nextStep ->
                sendMetric(Metric.EventType.Track, "[${data.operationType.metricName}] - Entered Correct Verification Code")
                moveToNextStep(nextStep)
            }.onFailure { onError(OwnIdException.map("OtpAuthStep.doOtpAuthRequest: ${it.message}", it)) }
        }
    }

    @MainThread
    internal fun onResend() {
        OwnIdInternalLogger.logD(this, "onResend", "Invoked")
        sendMetric(Metric.EventType.Click, "User select: Resend")

        mainHandler.removeCallbacks(resendButtonRunnable)
        mainHandler.postDelayed(resendButtonRunnable, 15_000)
        updateState { copy(isBusy = true, resendVisible = false, error = null) }

        doResendRequest {
            updateState { copy(isBusy = false) }
            mapCatching {
                // Ignoring next step
            }.onFailure { onError(OwnIdException.map("OtpAuthStep.onResend: ${it.message}", it)) }
        }
    }

    @MainThread
    internal fun onNotYou() {
        OwnIdInternalLogger.logD(this, "onNotYou", "Invoked")
        sendMetric(Metric.EventType.Click, "Clicked Not You")

        if (ownIdFlowData.ownIdCore.configuration.server.enableRegistrationFromLogin) {
            val error = state.value?.error
            updateState { copy(isBusy = true, error = null) }

            if (error is OwnIdFlowError && error.flowFinished) {
                ownIdFlowData.useLoginId = false
                moveToNextStep(InitStep.create(ownIdFlowData, onNextStep))
            } else {
                doNotYouRequest {
                    updateState { copy(isBusy = false) }
                    mapCatching { nextStep ->
                        moveToNextStep(nextStep)
                    }.onFailure { onError(OwnIdException.map("OtpAuthStep.onNotYou: ${it.message}", it)) }
                }
            }
        } else {
            onCancel(OwnIdFlowCanceled.OTP)
        }
    }

    @MainThread
    private fun doOtpAuthRequest(code: String, callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            val postData = JSONObject().put("code", code).toString()

            doPostRequest(ownIdFlowData, data.url, postData) {
                if (ownIdFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }

    @MainThread
    private fun doResendRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            doPostRequest(ownIdFlowData, data.resendUrl, "{}") {
                if (ownIdFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }

    @MainThread
    private fun doNotYouRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            //do not sent "loginId", if it set the IdCollect step will be skipped
            val postData = JSONObject().put("supportsFido2", ownIdFlowData.ownIdCore.configuration.isFidoPossible()).toString()

            doPostRequest(ownIdFlowData, data.restartUrl, postData) {
                if (ownIdFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}