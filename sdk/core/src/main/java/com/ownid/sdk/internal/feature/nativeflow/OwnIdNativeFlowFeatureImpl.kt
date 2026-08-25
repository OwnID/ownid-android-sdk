package com.ownid.sdk.internal.feature.nativeflow

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdResponse
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeature.Companion.getOwnIdCore
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeature.Companion.getOwnIdInstanceOrNull
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeature.Companion.toOwnIdFlowData
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowFeature.Companion.toRestoredOwnIdFlowData
import com.ownid.sdk.internal.feature.nativeflow.steps.DoneStep
import com.ownid.sdk.internal.feature.nativeflow.steps.idcollect.IdCollectStep
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import com.ownid.sdk.internal.feature.nativeflow.steps.webapp.OwnIdWebAppActivity
import com.ownid.sdk.internal.feature.nativeflow.steps.webapp.WebAppStep
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@InternalOwnIdAPI
internal class OwnIdNativeFlowFeatureImpl : OwnIdNativeFlowFeature {

    private companion object {
        private const val CORE_RESTORE_WAIT_TIMEOUT_MILLIS = 5_000L
        private const val CORE_RESTORE_CHECK_INTERVAL_MILLIS = 50L
        private const val KEY_CORE_RESTORE_WAIT_DEADLINE = "com.ownid.sdk.internal.intent.KEY_CORE_RESTORE_WAIT_DEADLINE"
    }

    private var viewModel: OwnIdNativeFlowViewModelInt? = null
    private var terminalResultSent: Boolean = false
    private var restoreWaitJob: Job? = null
    private var pendingOtpSnapshot: Bundle? = null
    private var restoreWaitDeadlineElapsedRealtime: Long? = null

    override fun onCreate(activity: AppCompatActivity, savedInstanceState: Bundle?) {
        val viewModel = ViewModelProvider(activity).get(OwnIdNativeFlowViewModelInt::class.java).also { this.viewModel = it }
        val webAppLauncher = activity.activityResultRegistry.register(
            OwnIdWebAppActivity.KEY_RESULT_REGISTRY, activity, ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val step = viewModel.ownIdFlowStep.value
            (step as? WebAppStep)?.onWebAppResult(result) ?: run {
                val message = "Not a WebApp step: ${step?.let { it::class.java.simpleName }}: Ignoring"
                OwnIdInternalLogger.logW(this, "activityResultRegistry", message)
            }
        }

        if (viewModel.ownIdFlowStep.value == null) {
            val restoredOtpSnapshot = savedInstanceState?.getBundle(OtpRecoveryState.BUNDLE_KEY)
            if (restoredOtpSnapshot != null) {
                val snapshotState = runCatching { OtpRecoveryState.fromBundle(restoredOtpSnapshot) }.getOrElse {
                    restoreOtp(activity, savedInstanceState, viewModel)
                    return
                }
                val restoredState = requireNotNull(savedInstanceState)
                if (restoredState.containsKey(KEY_CORE_RESTORE_WAIT_DEADLINE)) {
                    awaitOwnIdCore(activity, restoredState, snapshotState, viewModel, webAppLauncher)
                    return
                }

                val instance = runCatching { activity.intent.getOwnIdInstanceOrNull() }.getOrElse {
                    sendCoreLookupFailure(activity, it)
                    return
                }
                if (instance == null) {
                    awaitOwnIdCore(activity, restoredState, snapshotState, viewModel, webAppLauncher)
                    return
                }
            }
        }

        val ownIdCore = runCatching { activity.intent.getOwnIdCore() }.getOrElse {
            sendCoreLookupFailure(activity, it)
            return
        }

        continueOnCreate(activity, savedInstanceState, ownIdCore, viewModel, webAppLauncher)
    }

    private fun continueOnCreate(
        activity: AppCompatActivity,
        savedInstanceState: Bundle?,
        ownIdCore: OwnIdCoreImpl,
        viewModel: OwnIdNativeFlowViewModelInt,
        webAppLauncher: ActivityResultLauncher<Intent>,
    ) {
        activity.resources.configuration.setLocale(ownIdCore.localeService.currentOwnIdLocale.locale)

        viewModel.ownIdFlowStep.observe(activity) { step ->
            if (step == null || terminalResultSent || step === viewModel.presentedStep) {
                OwnIdInternalLogger.logD(this, "ownIdFlowStep", "New step: $step: Ignoring")
                return@observe
            }
            OwnIdInternalLogger.logD(this, "ownIdFlowStep", "New step: $step")

            if (step is IdCollectStep && ownIdCore.configuration.isServerConfigurationSet.not()) {
                sendResult(activity, Result.failure(OwnIdException("Server configuration is required for Login ID collection")))
                return@observe
            }

            viewModel.presentedStep = step

            when (step) {
                is DoneStep -> sendResult(activity, step.getOwnIdResponse(activity))
                is WebAppStep -> step.run(activity, webAppLauncher)
                else -> step.run(activity)
            }
        }

        if (viewModel.ownIdFlowStep.value != null) return

        if (savedInstanceState == null) {
            runCatching { activity.intent.toOwnIdFlowData() }
                .onSuccess { viewModel.startFlow(it) }
                .onFailure { sendResult(activity, Result.failure(OwnIdException("OwnIdFlowActivity.onCreate: ${it.message}", it))) }
            return
        }

        restoreOtp(activity, savedInstanceState, viewModel)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        OwnIdInternalLogger.logD(this, "onSaveInstanceState", "Invoked")
        if (terminalResultSent) return

        pendingOtpSnapshot?.let {
            outState.putBundle(OtpRecoveryState.BUNDLE_KEY, it)
            restoreWaitDeadlineElapsedRealtime?.let { deadline ->
                outState.putLong(KEY_CORE_RESTORE_WAIT_DEADLINE, deadline)
            }
            return
        }

        val step = viewModel?.ownIdFlowStep?.value as? OtpAuthStep ?: return
        if (step.isRestorable.not()) return

        outState.putBundle(OtpRecoveryState.BUNDLE_KEY, OtpRecoveryState.from(step).toBundle())
    }

    @Suppress("DEPRECATION")
    override fun sendResult(activity: AppCompatActivity, result: Result<OwnIdResponse>) {
        if (terminalResultSent) {
            OwnIdInternalLogger.logD(this, "sendResult", "Terminal result already sent: Ignoring")
            return
        }
        terminalResultSent = true
        restoreWaitJob?.cancel()
        viewModel?.ownIdFlowStep?.value?.ownIdNativeFlowData?.canceller?.cancel()

        OwnIdInternalLogger.logD(this, "sendResult", "result: $result")
        activity.setResult(AppCompatActivity.RESULT_OK, Intent().putExtra(OwnIdActivity.KEY_RESULT, result))
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    private fun awaitOwnIdCore(
        activity: AppCompatActivity,
        savedInstanceState: Bundle,
        snapshotState: OtpRecoveryState,
        viewModel: OwnIdNativeFlowViewModelInt,
        webAppLauncher: ActivityResultLauncher<Intent>,
    ) {
        val now = SystemClock.elapsedRealtime()
        // The elapsed-time restore budget intentionally continues while the activity is stopped.
        val waitDeadline = if (savedInstanceState.containsKey(KEY_CORE_RESTORE_WAIT_DEADLINE)) {
            minOf(
                savedInstanceState.getLong(KEY_CORE_RESTORE_WAIT_DEADLINE),
                snapshotState.expiresAtElapsedRealtime,
            )
        } else {
            minOf(
                now + CORE_RESTORE_WAIT_TIMEOUT_MILLIS,
                snapshotState.expiresAtElapsedRealtime,
            )
        }
        if (waitDeadline <= now) {
            sendResult(activity, Result.failure(OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING)))
            return
        }

        pendingOtpSnapshot = savedInstanceState.getBundle(OtpRecoveryState.BUNDLE_KEY)
        restoreWaitDeadlineElapsedRealtime = waitDeadline
        val waitJob = activity.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    val remainingMillis = waitDeadline - SystemClock.elapsedRealtime()
                    if (remainingMillis <= 0L) {
                        sendResult(activity, Result.failure(OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING)))
                        return@launch
                    }
                    if (activity.intent.getOwnIdInstanceOrNull() != null) break
                    delay(minOf(CORE_RESTORE_CHECK_INTERVAL_MILLIS, remainingMillis))
                }
                val ownIdCore = runCatching { activity.intent.getOwnIdCore() }.getOrElse {
                    sendCoreLookupFailure(activity, it)
                    return@launch
                }

                continueOnCreate(
                    activity,
                    savedInstanceState,
                    ownIdCore,
                    viewModel,
                    webAppLauncher,
                )
            } finally {
                pendingOtpSnapshot = null
                restoreWaitDeadlineElapsedRealtime = null
                restoreWaitJob = null
            }
        }
        restoreWaitJob = waitJob
        waitJob.start()
    }

    private fun sendCoreLookupFailure(activity: AppCompatActivity, cause: Throwable) {
        OwnIdInternalLogger.logW(this, "onCreate", cause.message, cause)
        sendResult(activity, Result.failure(OwnIdException("OwnIdFlowActivity.onCreate: ${cause.message}", cause)))
    }

    private fun restoreOtp(activity: AppCompatActivity, savedInstanceState: Bundle, viewModel: OwnIdNativeFlowViewModelInt) {
        runCatching {
            val bundle = requireNotNull(savedInstanceState.getBundle(OtpRecoveryState.BUNDLE_KEY)) { "OTP snapshot is missing" }
            val state = OtpRecoveryState.fromBundle(bundle)
            val flowData = activity.intent.toRestoredOwnIdFlowData(state)

            OwnIdInternalLogger.setFlowContext(flowData.context)
            flowData.ownIdCore.eventsService.setFlowContext(flowData.context)
            flowData.ownIdCore.eventsService.setFlowLoginId(flowData.loginId.value)

            viewModel.restoreOtpFlow(flowData, state.otpData)
        }.onFailure {
            val interruption = OwnIdFlowCanceled(OwnIdFlowCanceled.RESULT_PENDING)
            OwnIdInternalLogger.logW(this, "restoreOtp", interruption.message, it, errorMessage = it.message)
            sendResult(activity, Result.failure(interruption))
        }
    }
}
