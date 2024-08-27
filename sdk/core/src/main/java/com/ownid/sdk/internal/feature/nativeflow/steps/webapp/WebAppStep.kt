package com.ownid.sdk.internal.feature.nativeflow.steps.webapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.nativeflow.AbstractStep
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.feature.nativeflow.steps.DoneStep
import com.ownid.sdk.internal.feature.nativeflow.steps.SuccessStep
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class WebAppStep private constructor(
    ownIdNativeFlowData: OwnIdNativeFlowData,
    onNextStep: (AbstractStep) -> Unit,
    private val uri: Uri,
    private val redirectUrl: String
) : AbstractStep(ownIdNativeFlowData, onNextStep) {

    @InternalOwnIdAPI
    internal object Factory : AbstractStep.Factory {
        private const val TYPE: String = "showQr"

        @Throws(JSONException::class)
        override fun isThisStep(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData): Boolean =
            TYPE.equals(stepJson.optString("type"), ignoreCase = true) && ownIdNativeFlowData.qr.not()

        @Throws(IllegalArgumentException::class, JSONException::class)
        override fun create(stepJson: JSONObject, ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (AbstractStep) -> Unit): WebAppStep {
            val stepData = stepJson.getJSONObject("data")
            val url = stepData.optString("url").ifBlank { throw IllegalArgumentException("WebAppStep.create: 'url' cannot be empty") }
            val redirectUrl = Uri.parse(ownIdNativeFlowData.ownIdCore.configuration.getRedirectUri()).buildUpon()
                .appendQueryParameter("context", ownIdNativeFlowData.context)
                .build()
                .toString()

            return WebAppStep(ownIdNativeFlowData, onNextStep, Uri.parse(url), redirectUrl)
        }
    }

    @MainThread
    internal fun run(activity: FragmentActivity, webAppLauncher: ActivityResultLauncher<Intent>) {
        runCatching {
            val webAppUri: Uri = uri.buildUpon()
                .apply { if (ownIdNativeFlowData.loginId.isNotEmpty()) appendQueryParameter("e", ownIdNativeFlowData.loginId.value) }
                .appendQueryParameter("redirectURI", redirectUrl)
                .build()

            webAppLauncher.launch(OwnIdWebAppActivity.createIntent(activity, webAppUri.toString()))

        }.onFailure { onError(OwnIdException.map("WebAppStep.run: ${it.message}", it)) }
    }

    @MainThread
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    internal fun onWebAppResult(result: ActivityResult) {
        OwnIdInternalLogger.logD(this, "onWebAppResult", result.toString())

        if (ownIdNativeFlowData.canceller.isCanceled) return

        if (result.resultCode != Activity.RESULT_OK) {
            onCancel(OwnIdFlowCanceled.WEB_APP)
            return
        }

        runCatching { (result.data?.getSerializableExtra(OwnIdWebAppActivity.KEY_RESULT) as Result<String?>).getOrThrow() }
            .onSuccess {
                when {
                    it.isNullOrBlank() -> onCancel(OwnIdFlowCanceled.WEB_APP)
                    it.equals(redirectUrl, ignoreCase = true).not() -> onError(OwnIdException("WebAppStep. Wrong redirectURL: $it"))
                    else -> moveToNextStep(SuccessStep.Factory.create(JSONObject(), ownIdNativeFlowData, onNextStep))
                }
            }.onFailure { onError(OwnIdException.map("WebAppStep.onWebAppResult: ${it.message}", it)) }
    }

    @MainThread
    private fun onError(error: OwnIdException) {
        moveToNextStep(DoneStep(ownIdNativeFlowData, onNextStep, Result.failure(error)))
    }
}