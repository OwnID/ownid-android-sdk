package com.ownid.sdk.internal.flow

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RestrictTo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdInternalLogger
import com.ownid.sdk.internal.flow.steps.DoneStep
import com.ownid.sdk.internal.flow.steps.webapp.OwnIdWebAppActivity
import com.ownid.sdk.internal.flow.steps.webapp.WebAppStep

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdFlowActivity : AppCompatActivity() {

    internal companion object {
        internal const val KEY_RESULT = "com.ownid.sdk.internal.intent.KEY_RESULT"

        private const val KEY_INSTANCE_NAME = "com.ownid.sdk.internal.intent.KEY_INSTANCE_NAME"
        private const val KEY_FLOW_TYPE = "com.ownid.sdk.internal.intent.KEY_FLOW_TYPE"
        private const val KEY_LOGIN_TYPE = "com.ownid.sdk.internal.intent.KEY_LOGIN_TYPE"
        private const val KEY_LOGIN_ID = "com.ownid.sdk.internal.intent.KEY_LOGIN_ID"
        private const val KEY_START_FROM = "com.ownid.sdk.internal.intent.KEY_START_FROM"

        private const val KEY_CURRENT_STEP = "com.ownid.sdk.internal.intent.KEY_CURRENT_STEP"

        internal fun createIntent(
            context: Context, instanceName: InstanceName, flowType: OwnIdFlowType, loginType: OwnIdLoginType?, loginId: String, startFrom: String
        ): Intent =
            Intent(context, OwnIdFlowActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(KEY_INSTANCE_NAME, instanceName.toString())
                .putExtra(KEY_FLOW_TYPE, flowType)
                .putExtra(KEY_LOGIN_TYPE, loginType)
                .putExtra(KEY_LOGIN_ID, loginId)
                .putExtra(KEY_START_FROM, startFrom)
    }

    private var currentStep: String? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        currentStep = savedInstanceState?.getString(KEY_CURRENT_STEP)
        OwnIdInternalLogger.logD(this, "onCreate", "CurrentStep: $currentStep")

        val ownIdFlowData = runCatching {
            val instanceName = InstanceName(intent.getStringExtra(KEY_INSTANCE_NAME)!!)
            val ownIdCore = OwnId.getInstanceOrThrow<OwnIdInstance>(instanceName).ownIdCore as OwnIdCoreImpl
            val flowType = intent.getSerializableExtra(KEY_FLOW_TYPE) as OwnIdFlowType
            val loginType = intent.getSerializableExtra(KEY_LOGIN_TYPE) as? OwnIdLoginType
            val loginId = intent.getStringExtra(KEY_LOGIN_ID)!!

            val ownIdLoginId = OwnIdLoginId.fromString(loginId, ownIdCore.configuration)

            resources.configuration.setLocale(ownIdCore.localeService.currentOwnIdLocale.locale)

            OwnIdFlowData(ownIdCore, flowType, loginType, ownIdLoginId)
        }.getOrElse {
            OwnIdInternalLogger.logW(this, "onCreate", it.message, it)
            sendResult(Result.failure(OwnIdException("OwnIdFlowActivity.onCreate: ${it.message}", it)))
            return
        }

        supportFragmentManager.setFragmentResultListener(AbstractStepUI.KEY_RESULT_UI_ERROR, this) { _, bundle ->
            val cause = bundle.getSerializable(AbstractStepUI.KEY_RESULT_UI_ERROR) as? Throwable
            sendResult(Result.failure(OwnIdException("Error creating step UI: ${cause?.message}", cause)))
        }

        val ownIdFlowViewModel = ViewModelProvider(this@OwnIdFlowActivity).get(OwnIdFlowViewModel::class.java)

        val webAppLauncher = activityResultRegistry.register(
            OwnIdWebAppActivity.KEY_RESULT_REGISTRY, this, ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val step = ownIdFlowViewModel.ownIdFlowStep.value
            (step as? WebAppStep)?.onWebAppResult(result) ?: run {
                val message = "Not a WebApp step: ${step?.let { it::class.java.simpleName }}: Ignoring"
                OwnIdInternalLogger.logW(this@OwnIdFlowActivity, "activityResultRegistry", message)
            }
        }

        ownIdFlowViewModel.ownIdFlowStep.observe(this) { step ->
            if (step == null) return@observe
            if (currentStep == step.toString()) {
                OwnIdInternalLogger.logD(this, "ownIdFlowStep", "New step: $step: Ignoring")
                return@observe
            }
            OwnIdInternalLogger.logD(this, "ownIdFlowStep", "New step: $step")
            currentStep = step.toString()
            when (step) {
                is DoneStep -> sendResult(step.getOwnIdResponse(this))
                is WebAppStep -> step.run(this, webAppLauncher)
                else -> step.run(this)
            }
        }

        if (currentStep == null) {
            OwnIdInternalLogger.logI(this, "onCreate", "System supports FIDO: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.P}")
            ownIdFlowViewModel.startFlow(ownIdFlowData, intent.getStringExtra(KEY_START_FROM))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        OwnIdInternalLogger.logD(this, "onSaveInstanceState", "Invoked")
        outState.putString(KEY_CURRENT_STEP, currentStep)
    }

    @Suppress("DEPRECATION")
    private fun sendResult(result: Result<OwnIdResponse>) {
        OwnIdInternalLogger.logD(this, "sendResult", "result: $result")
        setResult(RESULT_OK, Intent().putExtra(KEY_RESULT, result))
        finish()
        overridePendingTransition(0, 0)
    }
}