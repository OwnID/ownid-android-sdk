package com.ownid.sdk.internal.feature.nativeflow

import android.os.Handler
import android.os.Looper
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.Metadata
import com.ownid.sdk.internal.component.events.Metric
import com.ownid.sdk.internal.feature.nativeflow.steps.DoneStep
import com.ownid.sdk.internal.feature.nativeflow.steps.SuccessStep
import com.ownid.sdk.internal.feature.nativeflow.steps.fido.FidoLoginAuthStep
import com.ownid.sdk.internal.feature.nativeflow.steps.fido.FidoRegisterAuthStep
import com.ownid.sdk.internal.feature.nativeflow.steps.idcollect.IdCollectStep
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import com.ownid.sdk.internal.feature.nativeflow.steps.webapp.WebAppStep
import kotlinx.coroutines.runBlocking
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal abstract class AbstractStep(
    internal val ownIdNativeFlowData: OwnIdNativeFlowData,
    protected val onNextStep: (AbstractStep) -> Unit,
    private val networkHandler: Handler? = Handler(Looper.getMainLooper())
) {

    @InternalOwnIdAPI
    internal interface Factory {
        @Throws(JSONException::class)
        fun isThisStep(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData): Boolean

        @Throws(OwnIdException::class, JSONException::class)
        fun create(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): AbstractStep
    }

    private val knownSteps = listOf(
        IdCollectStep.Factory,
        FidoLoginAuthStep.Factory,
        FidoRegisterAuthStep.Factory,
        OtpAuthStep.Factory,
        WebAppStep.Factory,
        SuccessStep.Factory
    )

    @Throws(OwnIdNativeFlowError::class, IllegalArgumentException::class, JSONException::class)
    protected fun parseResponse(response: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): AbstractStep {
        OwnIdInternalLogger.logD(this, "parseResponse", "Invoked")

        response.optJSONObject("error")?.let { errorJson -> throw OwnIdNativeFlowError.fromJson(errorJson) }

        return response.optJSONObject("step")?.let { stepJson ->
            knownSteps.firstOrNull { stepFactory -> stepFactory.isThisStep(stepJson, ownIdNativeFlowData) }
                ?.create(stepJson, ownIdNativeFlowData, onNextStep)
                ?: throw IllegalArgumentException("${this::class.java.simpleName}.parseResponse: Unknown step: $stepJson")
        } ?: throw IllegalArgumentException("${this::class.java.simpleName}.parseResponse: no next step, no error")
    }

    protected val mainHandler: Handler = Handler(Looper.getMainLooper())

    @CallSuper
    @MainThread
    internal open fun run(activity: FragmentActivity) {
        OwnIdInternalLogger.logD(this, "run", "Invoked")
    }

    @CallSuper
    @MainThread
    internal open fun moveToNextStep(nextStep: AbstractStep) {
        OwnIdInternalLogger.logD(this, "moveToNextStep", nextStep::class.java.simpleName)
        mainHandler.post { onNextStep(nextStep) }
    }

    @CallSuper
    @MainThread
    internal open fun onCancel(type: String) {
        OwnIdInternalLogger.logI(this, "onCancel", type)

        sendMetric(Metric.EventType.Click, "Clicked Cancel")
        moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.failure(OwnIdFlowCanceled(type))))
    }

    internal open fun getMetricViewedAction(): String = "Viewed ${this::class.java.simpleName}"

    internal open fun getMetricSource(): String? = null

    @MainThread
    protected fun sendMetric(type: Metric.EventType, action: String, errorMessage: String? = null, errorCode: String? = null) {
        val returningUser = runBlocking { ownIdNativeFlowData.ownIdCore.repository.getLoginId() }?.isNotBlank() ?: false
        ownIdNativeFlowData.ownIdCore.eventsService.sendMetric(
            ownIdNativeFlowData.flowType, type, action, Metadata(returningUser = returningUser), getMetricSource(), errorMessage, errorCode
        )
    }

    override fun toString(): String = this::class.java.simpleName + "#" + this.hashCode()

    @JvmSynthetic
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public fun doPostRequest(ownIdNativeFlowData: OwnIdNativeFlowData, url: HttpUrl, postData: String, callback: OwnIdCallback<String>) {
        OwnIdInternalLogger.logD(this, "doPostRequest", "$url")

        val request: Request = Request.Builder()
            .url(url)
            .header("User-Agent", ownIdNativeFlowData.ownIdCore.configuration.userAgent)
            .header("Accept-Language", ownIdNativeFlowData.ownIdCore.localeService.currentOwnIdLocale.serverLanguageTag)
            .post(postData.toRequestBody(DEFAULT_MEDIA_TYPE))
            .cacheControl(CACHE_CONTROL_FORCE_NETWORK_NO_CACHE)
            .build()

        ownIdNativeFlowData.ownIdCore.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnHandler { callback(Result.failure(OwnIdException("Request fail ($url) ${e.message}", e))) }
            }

            override fun onResponse(call: Call, response: Response) {
                OwnIdInternalLogger.logD(this@AbstractStep, "doPostRequest.onResponse", "(${url.encodedPath}): ${response.code}")
                val result = runCatching {
                    if (response.isSuccessful) response.use { it.body!!.string() }
                    else throw OwnIdException("Server response ($url): ${response.code} ${response.message}")
                }
                runOnHandler { callback(result) }
            }
        })
    }

    private fun runOnHandler(action: Runnable) {
        networkHandler?.post(action) ?: action.run()
    }

    private companion object {
        private val DEFAULT_MEDIA_TYPE: MediaType = "application/json".toMediaType()
        private val CACHE_CONTROL_FORCE_NETWORK_NO_CACHE: CacheControl = CacheControl.Builder().noCache().noStore().build()
    }
}