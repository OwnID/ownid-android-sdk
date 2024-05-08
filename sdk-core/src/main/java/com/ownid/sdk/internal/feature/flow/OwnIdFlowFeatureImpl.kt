package com.ownid.sdk.internal.feature.flow

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.flow.OwnIdFlowFeature.Companion.toOwnIdFlowData
import com.ownid.sdk.internal.feature.flow.steps.DoneStep
import com.ownid.sdk.internal.feature.flow.steps.webapp.OwnIdWebAppActivity
import com.ownid.sdk.internal.feature.flow.steps.webapp.WebAppStep

@InternalOwnIdAPI
internal class OwnIdFlowFeatureImpl : OwnIdFlowFeature {

    private var currentStep: String? = null

    override fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?) {
        currentStep = savedInstanceState?.getString(OwnIdFlowFeature.KEY_CURRENT_STEP)

        OwnIdInternalLogger.logD(this, "onCreate", "CurrentStep: $currentStep")

        val ownIdFlowData = runCatching {
            activity.intent.toOwnIdFlowData()
        }.getOrElse {
            OwnIdInternalLogger.logW(this, "onCreate", it.message, it)
            sendResult(activity, Result.failure(OwnIdException("OwnIdFlowActivity.onCreate: ${it.message}", it)))
            return
        }

        activity.resources.configuration.setLocale(ownIdFlowData.ownIdCore.localeService.currentOwnIdLocale.locale)

        val viewModel = ViewModelProvider(activity).get(OwnIdFlowViewModelInt::class.java)

        val webAppLauncher = activity.activityResultRegistry.register(
            OwnIdWebAppActivity.KEY_RESULT_REGISTRY, activity, ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val step = viewModel.ownIdFlowStep.value
            (step as? WebAppStep)?.onWebAppResult(result) ?: run {
                val message = "Not a WebApp step: ${step?.let { it::class.java.simpleName }}: Ignoring"
                OwnIdInternalLogger.logW(this, "activityResultRegistry", message)
            }
        }

        viewModel.ownIdFlowStep.observe(activity) { step ->
            if (step == null) return@observe
            if (currentStep == step.toString()) {
                OwnIdInternalLogger.logD(this, "ownIdFlowStep", "New step: $step: Ignoring")
                return@observe
            }
            OwnIdInternalLogger.logD(this, "ownIdFlowStep", "New step: $step")
            currentStep = step.toString()
            when (step) {
                is DoneStep -> sendResult(activity, step.getOwnIdResponse(activity))
                is WebAppStep -> step.run(activity, webAppLauncher)
                else -> step.run(activity)
            }
        }

        if (currentStep == null) {
            viewModel.startFlow(ownIdFlowData)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        OwnIdInternalLogger.logD(this, "onSaveInstanceState", "Invoked")
        outState.putString(OwnIdFlowFeature.KEY_CURRENT_STEP, currentStep)
    }

    @Suppress("DEPRECATION")
    override fun sendResult(activity: AppCompatActivity, result: Result<OwnIdResponse>) {
        OwnIdInternalLogger.logD(this, "sendResult", "result: $result")
        activity.setResult(AppCompatActivity.RESULT_OK, Intent().putExtra(OwnIdActivity.KEY_RESULT, result))
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }
}