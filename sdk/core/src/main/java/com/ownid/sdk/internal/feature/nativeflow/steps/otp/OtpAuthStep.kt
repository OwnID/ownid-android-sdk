package com.ownid.sdk.internal.feature.nativeflow.steps.otp

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowError
import com.ownid.sdk.internal.feature.nativeflow.steps.DoneStep
import com.ownid.sdk.internal.feature.nativeflow.steps.InitStep
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OtpAuthStep private constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    internal val data: Data
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

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
        override fun isThisStep(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData): Boolean {
            val type = stepJson.optString("type")
            return TYPES.any { it.equals(type, ignoreCase = true) }
        }

        @Throws(IllegalArgumentException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): OtpAuthStep {
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
                    VerificationType.values().firstOrNull { it.name.equals(type, ignoreCase = true) }
                        ?: throw IllegalArgumentException("OtpAuthStep.create: Unknown 'verificationType': $type")
                },
                if (stepJson.optString("type").equals("loginIDAuthorization", ignoreCase = true)) OperationType.Sign
                else OperationType.Verify
            )

            return OtpAuthStep(ownIdNativeFlowData, onNextStep, data)
        }
    }

    @InternalOwnIdAPI
    internal data class OtpAuthState(
        val isBusy: Boolean = false,
        val resendVisible: Boolean = false,
        val notYouVisible: Boolean = true,
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

    override fun getMetricSource(): String = data.operationType.metricName

    @MainThread
    private fun onError(error: OwnIdException) {
        if (error !is OwnIdNativeFlowError) OwnIdInternalLogger.logW(this, "onError", error.message, error)
        else when (error.errorCode) {
            OwnIdNativeFlowError.CodeServer.WRONG_CODE,
            OwnIdNativeFlowError.CodeServer.WRONG_CODE_LIMIT_REACHED ->
                sendMetric(Metric.EventType.Track, "[${data.operationType.metricName}] - Entered Wrong Verification Code", errorCode = error.errorCode)

            OwnIdNativeFlowError.CodeServer.ACCOUNT_NOT_FOUND,
            OwnIdNativeFlowError.CodeServer.ACCOUNT_IS_BLOCKED,
            OwnIdNativeFlowError.CodeServer.USER_NOT_FOUND,
            OwnIdNativeFlowError.CodeServer.REQUIRES_BIOMETRIC_INPUT ->
                sendMetric(Metric.EventType.Track, "[${data.operationType.metricName}] - Entered Correct Verification Code")
        }

        sendMetric(
            Metric.EventType.Error, "Viewed Error",
            if (error is OwnIdNativeFlowError) error.userMessage else error.message,
            if (error is OwnIdNativeFlowError) error.errorCode else null
        )
        val notYouVisible = error !is OwnIdNativeFlowError || error.errorCode != OwnIdNativeFlowError.CodeServer.WRONG_CODE_LIMIT_REACHED
        updateState { copy(otp = "", notYouVisible = notYouVisible, error = error) }
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
        sendMetric(Metric.EventType.Click, "Clicked Resend")

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

        if (ownIdNativeFlowData.ownIdCore.configuration.server.enableRegistrationFromLogin) {
            val error = state.value?.error
            updateState { copy(isBusy = true, error = null) }

            if (error is OwnIdNativeFlowError && error.flowFinished) {
                ownIdNativeFlowData.useLoginId = false
                moveToNextStep(InitStep.create(ownIdNativeFlowData, onNextStep))
            } else {
                doNotYouRequest {
                    updateState { copy(isBusy = false) }
                    mapCatching { nextStep ->
                        moveToNextStep(nextStep)
                    }.onFailure { onError(OwnIdException.map("OtpAuthStep.onNotYou: ${it.message}", it)) }
                }
            }
        } else {
            moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.failure(OwnIdFlowCanceled(OwnIdFlowCanceled.OTP))))
        }
    }

    @MainThread
    private fun doOtpAuthRequest(code: String, callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            val postData = JSONObject().put("code", code).toString()

            doPostRequest(ownIdNativeFlowData, data.url, postData) {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdNativeFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }

    @MainThread
    private fun doResendRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            doPostRequest(ownIdNativeFlowData, data.resendUrl, "{}") {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdNativeFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }

    @MainThread
    private fun doNotYouRequest(callback: OwnIdCallback<AbstractStep>) {
        runCatching {
            //do not sent "loginId", if it set the IdCollect step will be skipped
            val postData = JSONObject().put("supportsFido2", ownIdNativeFlowData.ownIdCore.configuration.isFidoPossible()).toString()

            doPostRequest(ownIdNativeFlowData, data.restartUrl, postData) {
                if (ownIdNativeFlowData.canceller.isCanceled) return@doPostRequest
                mapCatching { parseResponse(JSONObject(it), ownIdNativeFlowData, onNextStep) }.callback()
            }
        }.getOrElse { callback(Result.failure(it)) }
    }
}