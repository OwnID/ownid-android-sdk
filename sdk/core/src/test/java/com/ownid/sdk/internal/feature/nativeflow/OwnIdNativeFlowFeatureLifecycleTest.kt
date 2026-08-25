package com.ownid.sdk.internal.feature.nativeflow

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdLogger
import com.ownid.sdk.R
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.exception.OwnIdFlowCanceled
import com.ownid.sdk.internal.feature.OwnIdActivity
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStepUI
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
public class OwnIdNativeFlowFeatureLifecycleTest {
    private lateinit var environment: NativeFlowTestEnvironment
    private val controllers = mutableListOf<ActivityController<OwnIdActivity>>()

    @Before
    public fun setUp() {
        environment = NativeFlowTestEnvironment(withServerConfiguration = false)
    }

    @After
    public fun tearDown() {
        controllers.asReversed().forEach { runCatching { it.close() } }
        environment.close()
    }

    @Test
    public fun configurationChange_retainsOtpStepSingleDialogAndPartialCode() {
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        val activityBefore = controller.get()
        val viewModelBefore = viewModel(activityBefore)
        val stepBefore = otpStep(activityBefore)

        otpEditText(activityBefore).setText(PARTIAL_OTP)
        idleMainLooper()

        assertThat(stepBefore.state.value?.otp).isEqualTo(PARTIAL_OTP)

        controller.recreate()
        idleMainLooper()

        val activityAfter = controller.get()
        assertThat(activityAfter).isNotSameInstanceAs(activityBefore)
        assertThat(viewModel(activityAfter)).isSameInstanceAs(viewModelBefore)
        assertThat(otpStep(activityAfter)).isSameInstanceAs(stepBefore)
        assertThat(otpFragments(activityAfter)).hasSize(1)
        assertThat(otpEditText(activityAfter).text.toString()).isEqualTo(PARTIAL_OTP)
    }

    @Test
    public fun configurationChange_retainedOtpStepWinsWhenSnapshotParsingWouldFail() {
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        val activityBefore = controller.get()
        val viewModelBefore = viewModel(activityBefore)
        val stepBefore = otpStep(activityBefore)

        mockkObject(OtpRecoveryState.Companion)
        try {
            every { OtpRecoveryState.fromBundle(any()) } throws IllegalArgumentException("rejected snapshot")
            controller.recreate()
            verify(exactly = 0) { OtpRecoveryState.fromBundle(any()) }
        } finally {
            unmockkObject(OtpRecoveryState.Companion)
        }
        idleMainLooper()

        val activityAfter = controller.get()
        assertThat(activityAfter.isFinishing).isFalse()
        assertThat(viewModel(activityAfter)).isSameInstanceAs(viewModelBefore)
        assertThat(otpStep(activityAfter)).isSameInstanceAs(stepBefore)
        assertThat(otpFragments(activityAfter)).hasSize(1)
    }

    @Test
    public fun processLikeRestore_waitsForMissingCoreThenRestoresOnce() {
        replaceEnvironment(registerInstance = false)
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        val activity = controller.get()

        assertThat(activity.isFinishing).isFalse()
        assertThat(viewModel(activity).ownIdFlowStep.value).isNull()

        environment.registerInstance()
        idleMainLooper(100, TimeUnit.MILLISECONDS)

        assertThat(activity.isFinishing).isFalse()
        assertThat(otpFragments(activity)).hasSize(1)

        idleMainLooper(6, TimeUnit.SECONDS)

        assertThat(activity.isFinishing).isFalse()
        assertThat(otpFragments(activity)).hasSize(1)
    }

    @Test
    public fun processLikeRestore_coreAppearsWhileActivityStoppedRestoresAfterResumeOnce() {
        replaceEnvironment(registerInstance = false)
        val originalStep = environment.restoredOtpStep(environment.flowData(restoredVerifier = SENTINEL_VERIFIER))
        val controller = launch(environment.savedState(originalStep))
        val activity = controller.get()

        controller.pause().stop()
        idleMainLooper(100, TimeUnit.MILLISECONDS)

        assertThat(activity.lifecycle.currentState).isEqualTo(Lifecycle.State.CREATED)
        assertThat(activity.isFinishing).isFalse()
        assertThat(otpFragments(activity)).isEmpty()

        environment.registerInstance()
        idleMainLooper(100, TimeUnit.MILLISECONDS)

        assertThat(activity.lifecycle.currentState).isEqualTo(Lifecycle.State.CREATED)
        assertThat(activity.isFinishing).isFalse()
        assertThat(shadowOf(activity).resultIntent).isNull()
        assertThat(otpFragments(activity)).isEmpty()

        controller.restart().start().resume()
        idleMainLooper()

        val restoredStep = otpStep(activity)
        assertThat(activity.isFinishing).isFalse()
        assertEquivalent(restoredStep, originalStep)
        assertThat(otpFragments(activity)).hasSize(1)

        idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertThat(otpFragments(activity)).hasSize(1)
    }

    @Test
    public fun processLikeRestore_recreatedWhileWaitingPreservesSnapshotAndRestoresSingleOtpUi() {
        replaceEnvironment(registerInstance = false)
        val originalStep = environment.restoredOtpStep(environment.flowData(restoredVerifier = SENTINEL_VERIFIER))
        val savedState = environment.savedState(originalStep)
        val originalSnapshot = OtpRecoveryState.fromBundle(
            requireNotNull(savedState.getBundle(OtpRecoveryState.BUNDLE_KEY)),
        )
        val controller = launch(savedState)
        val activityBefore = controller.get()

        assertThat(activityBefore.isFinishing).isFalse()
        assertThat(otpFragments(activityBefore)).isEmpty()

        controller.recreate()
        idleMainLooper()

        val activityAfter = controller.get()
        assertThat(activityAfter).isNotSameInstanceAs(activityBefore)
        assertThat(activityAfter.isFinishing).isFalse()
        assertThat(otpFragments(activityAfter)).isEmpty()

        environment.registerInstance()
        idleMainLooper(100, TimeUnit.MILLISECONDS)

        val restoredStep = otpStep(activityAfter)
        assertThat(activityAfter.isFinishing).isFalse()
        assertThat(restoredStep.ownIdNativeFlowData.verifier).isEqualTo(originalSnapshot.verifier)
        assertThat(restoredStep.ownIdNativeFlowData.expiresAtElapsedRealtime)
            .isEqualTo(originalSnapshot.expiresAtElapsedRealtime)
        assertEquivalent(restoredStep, originalStep)
        assertThat(otpFragments(activityAfter)).hasSize(1)

        idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertThat(otpFragments(activityAfter)).hasSize(1)
    }

    @Test
    public fun processLikeRestore_expiredPersistedDeadlineWinsOverCoreAvailableBeforeRecreatedOnCreate() {
        replaceEnvironment(registerInstance = false)
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        val savedState = saveInstanceState(controller)
        controller.destroy()

        idleMainLooper(5, TimeUnit.SECONDS)
        environment.registerInstance()
        val recreatedActivity = launch(parcelCopy(savedState)).get()

        assertRestoreFailure(recreatedActivity)
        assertThat(viewModel(recreatedActivity).ownIdFlowStep.value).isNull()
    }

    @Test
    public fun processLikeRestore_snapshotExpiryShortensWait() {
        replaceEnvironment(registerInstance = false)
        val expiringFlow = environment.flowData(
            expiresAtElapsedRealtime = SystemClock.elapsedRealtime() + 200L,
        )
        val activity = launch(environment.savedState(environment.restoredOtpStep(expiringFlow))).get()

        idleMainLooper(150, TimeUnit.MILLISECONDS)
        assertThat(activity.isFinishing).isFalse()

        idleMainLooper(50, TimeUnit.MILLISECONDS)
        assertRestoreFailure(activity)
    }

    @Test
    public fun missingCore_freshAndSnapshotlessRestoredLaunchesFailWithoutWaiting() {
        replaceEnvironment(registerInstance = false)

        val freshActivity = trackedController(OwnIdNativeFlowType.LOGIN).create().get()
        val restoredActivity = create(Bundle()).get()

        assertCoreLookupFailure(freshActivity)
        assertCoreLookupFailure(restoredActivity)
    }

    @Test
    public fun fragmentSavedStateRestore_waitsForCoreAndRebindsSingleOtpUi() {
        val originalStep = environment.restoredOtpStep()
        val originalController = launch(environment.savedState(originalStep))
        val savedState = Bundle()
        originalController.pause().stop().saveInstanceState(savedState).destroy()

        replaceEnvironment(registerInstance = false)
        val restoredActivity = launch(parcelCopy(savedState)).get()
        assertThat(restoredActivity.isFinishing).isFalse()

        environment.registerInstance()
        idleMainLooper(100, TimeUnit.MILLISECONDS)

        val restoredStep = otpStep(restoredActivity)
        assertThat(restoredActivity.isFinishing).isFalse()
        assertEquivalent(restoredStep, originalStep)
        assertThat(otpFragments(restoredActivity)).hasSize(1)
        otpEditText(restoredActivity).setText(PARTIAL_OTP)
        idleMainLooper()
        assertThat(restoredStep.state.value?.otp).isEqualTo(PARTIAL_OTP)
        assertThat(otpFragments(restoredActivity)).hasSize(1)
    }

    @Test
    public fun processLikeRestore_missingCoreTimesOutAsInterruptedAndIgnoresLateCore() {
        replaceEnvironment(registerInstance = false)
        val activity = launch(environment.savedState(environment.restoredOtpStep())).get()

        idleMainLooper(5, TimeUnit.SECONDS)

        assertRestoreFailure(activity)
        environment.registerInstance()
        idleMainLooper(100, TimeUnit.MILLISECONDS)
        sendUiFailure(activity, "late result")

        assertRestoreFailure(activity)
    }

    @Test
    public fun onSave_waitingForCorePreservesOtpSnapshot() {
        replaceEnvironment(registerInstance = false)
        val sourceState = environment.savedState(environment.restoredOtpStep())
        val controller = launch(sourceState)

        val savedState = saveInstanceState(controller)

        val restored = OtpRecoveryState.fromBundle(requireNotNull(savedState.getBundle(OtpRecoveryState.BUNDLE_KEY)))
        val original = OtpRecoveryState.fromBundle(requireNotNull(sourceState.getBundle(OtpRecoveryState.BUNDLE_KEY)))
        assertThat(restored.verifier).isEqualTo(original.verifier)
        assertThat(restored.expiresAtElapsedRealtime).isEqualTo(original.expiresAtElapsedRealtime)
    }

    @Test
    public fun destroyWhileWaitingCancelsWithoutTerminalResult() {
        replaceEnvironment(registerInstance = false)
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        val activity = controller.get()

        controller.pause().stop().destroy()
        environment.registerInstance()
        idleMainLooper(6, TimeUnit.SECONDS)

        assertThat(activity.isDestroyed).isTrue()
        assertThat(shadowOf(activity).resultIntent).isNull()
    }

    @Test
    public fun validSnapshotWithMalformedInstanceIntentFailsInsteadOfWaiting() {
        replaceEnvironment(registerInstance = false)
        val intent = environment.intent().apply { removeExtra(INSTANCE_NAME_KEY) }
        val activity = trackedController(intent).create(environment.savedState(environment.restoredOtpStep())).get()

        assertThat(activity.isFinishing).isTrue()
        assertThat(result(activity).exceptionOrNull()).isInstanceOf(OwnIdException::class.java)
    }

    @Test
    public fun validSnapshotWithIncompatibleCoreFailsInsteadOfWaiting() {
        replaceEnvironment(registerInstance = false)
        val incompatibleCore = mockk<OwnIdCore> {
            every { instanceName } returns environment.instanceName
        }
        OwnId.putInstance(object : OwnIdInstance {
            override val ownIdCore = incompatibleCore
            override val ownIdIntegration = null
        })

        val activity = create(environment.savedState(environment.restoredOtpStep())).get()

        assertThat(activity.isFinishing).isTrue()
        assertThat(result(activity).exceptionOrNull()).isInstanceOf(OwnIdException::class.java)
    }

    @Test
    public fun processLikeRestore_createsNewLoginOtpStepWithoutServerConfiguration() {
        assertProcessLikeRestore(OwnIdNativeFlowType.LOGIN)
    }

    @Test
    public fun processLikeRestore_createsNewRegisterOtpStepWithoutServerConfiguration() {
        assertProcessLikeRestore(OwnIdNativeFlowType.REGISTER)
    }

    @Test
    public fun onSave_requestInFlight_omitsSnapshot() {
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        val activity = controller.get()
        val step = otpStep(activity)
        val editText = otpEditText(activity)
        editText.setText(FULL_OTP)
        idleMainLooper()

        assertThat(step.state.value?.isBusy).isTrue()
        editText.setText("5678")
        assertThat(editText.text.toString()).isEqualTo(FULL_OTP)
        assertThat(editText.isEnabled).isTrue()
        assertThat(editText.hasFocus()).isTrue()
        val savedState = saveInstanceState(controller)

        assertThat(savedState.containsKey(OtpRecoveryState.BUNDLE_KEY)).isFalse()
    }

    @Test
    public fun onSave_expiredOtp_omitsSnapshot() {
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        otpStep(controller.get()).ownIdNativeFlowData.expiresAtElapsedRealtime = SystemClock.elapsedRealtime()

        val savedState = saveInstanceState(controller)

        assertThat(savedState.containsKey(OtpRecoveryState.BUNDLE_KEY)).isFalse()
    }

    @Test
    public fun missingSnapshot_logsWarningAndFinishesAsInterrupted() {
        var warningMessage: String? = null
        var warningCause: Throwable? = null
        val loggingEnabled = OwnIdLogger.enabled
        OwnIdLogger.enabled = true
        OwnIdLogger.set("OwnID-SDK-Test", object : OwnIdLogger.Logger {
            override fun log(priority: Int, className: String, message: String, cause: Throwable?) {
                if (priority == Log.WARN && className.endsWith(":restoreOtp")) {
                    warningMessage = message
                    warningCause = cause
                }
            }
        })

        val activity = try {
            create(Bundle()).get()
        } finally {
            OwnIdLogger.set("OwnID-SDK")
            OwnIdLogger.enabled = loggingEnabled
        }

        assertRestoreFailure(activity)
        assertThat(warningMessage).contains("OwnID flow was interrupted before completion")
        assertThat(warningMessage).contains("OTP snapshot is missing")
        assertThat(warningCause?.message).isEqualTo("OTP snapshot is missing")
    }

    @Test
    public fun corruptSnapshot_finishesWithFailure() {
        assertCorruptSnapshotFinishes(registerInstance = true)
    }

    @Test
    public fun corruptSnapshot_withoutCore_failsFast() {
        assertCorruptSnapshotFinishes(registerInstance = false)
    }

    @Test
    public fun expiredSnapshot_finishesWithFailure() {
        assertExpiredSnapshotFinishes(registerInstance = true)
    }

    @Test
    public fun expiredSnapshot_withoutCore_failsFast() {
        assertExpiredSnapshotFinishes(registerInstance = false)
    }

    @Test
    public fun restoredOtpFragmentWithoutSnapshot_finishesInsteadOfSticking() {
        val controller = launch(environment.savedState(environment.restoredOtpStep()))
        assertThat(otpFragments(controller.get())).hasSize(1)

        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState).destroy()
        savedState.remove(OtpRecoveryState.BUNDLE_KEY)

        val restoredActivity = launch(parcelCopy(savedState)).get()

        assertRestoreFailure(restoredActivity)
    }

    @Test
    public fun repeatedTerminalResults_keepFirstResult() {
        val activity = launch(environment.savedState(environment.restoredOtpStep())).get()

        sendUiFailure(activity, "first")
        sendUiFailure(activity, "second")

        assertThat(result(activity).exceptionOrNull()?.message).contains("first")
        assertThat(result(activity).exceptionOrNull()?.message).doesNotContain("second")
    }

    private fun assertProcessLikeRestore(flowType: OwnIdNativeFlowType) {
        val sourceFlowData = environment.flowData(
            flowType = flowType,
            enableRegistrationFromLogin = true,
            redirectUri = SENTINEL_REDIRECT_URI,
            languageTag = SENTINEL_LANGUAGE_TAG,
            restoredVerifier = SENTINEL_VERIFIER,
        )
        val originalStep = environment.restoredOtpStep(sourceFlowData)
        assertThat(environment.configuration.isServerConfigurationSet).isFalse()
        val originalController = launch(environment.savedState(originalStep), flowType)
        val originalActivity = originalController.get()
        val originalViewModel = viewModel(originalActivity)
        val presentedStep = otpStep(originalActivity)

        otpEditText(originalActivity).setText(PARTIAL_OTP)
        idleMainLooper()

        val savedState = Bundle()
        originalController.pause().stop().saveInstanceState(savedState).destroy()
        val persistedState = parcelCopy(savedState)

        val restoredController = launch(persistedState, flowType)
        val restoredActivity = restoredController.get()
        val restoredViewModel = viewModel(restoredActivity)
        val restoredStep = otpStep(restoredActivity)

        assertThat(restoredActivity).isNotSameInstanceAs(originalActivity)
        assertThat(restoredViewModel).isNotSameInstanceAs(originalViewModel)
        assertThat(restoredStep).isNotSameInstanceAs(presentedStep)
        assertEquivalent(restoredStep, presentedStep)
        assertThat(restoredStep.state.value?.otp).isEmpty()
        assertThat(otpEditText(restoredActivity).text.toString()).isEmpty()
        assertThat(otpFragments(restoredActivity)).hasSize(1)
        assertThat(environment.mockWebServer.requestCount).isEqualTo(0)
    }

    private fun assertEquivalent(actual: OtpAuthStep, expected: OtpAuthStep) {
        assertThat(actual.data.url).isEqualTo(expected.data.url)
        assertThat(actual.data.restartUrl).isEqualTo(expected.data.restartUrl)
        assertThat(actual.data.resendUrl).isEqualTo(expected.data.resendUrl)
        assertThat(actual.data.otpLength).isEqualTo(expected.data.otpLength)
        assertThat(actual.data.verificationType).isEqualTo(expected.data.verificationType)
        assertThat(actual.data.operationType).isEqualTo(expected.data.operationType)

        val actualFlow = actual.ownIdNativeFlowData
        val expectedFlow = expected.ownIdNativeFlowData
        assertThat(actualFlow.flowType).isEqualTo(expectedFlow.flowType)
        assertThat(actualFlow.loginType).isEqualTo(expectedFlow.loginType)
        assertThat(actualFlow.loginId.localeKey).isEqualTo(expectedFlow.loginId.localeKey)
        assertThat(actualFlow.loginId.value).isEqualTo(expectedFlow.loginId.value)
        assertThat(actualFlow.verifier).isEqualTo(expectedFlow.verifier)
        assertThat(actualFlow.context).isEqualTo(expectedFlow.context)
        assertThat(actualFlow.statusFinalUrl).isEqualTo(expectedFlow.statusFinalUrl)
        assertThat(actualFlow.expiresAtElapsedRealtime).isEqualTo(expectedFlow.expiresAtElapsedRealtime)
        assertThat(actualFlow.enableRegistrationFromLogin).isEqualTo(expectedFlow.enableRegistrationFromLogin)
        assertThat(actualFlow.redirectUri).isEqualTo(expectedFlow.redirectUri)
        assertThat(actualFlow.languageTag).isEqualTo(expectedFlow.languageTag)
    }

    private fun assertCorruptSnapshotFinishes(registerInstance: Boolean) {
        if (!registerInstance) replaceEnvironment(registerInstance = false)
        val savedState = Bundle().apply {
            putBundle(OtpRecoveryState.BUNDLE_KEY, Bundle())
        }

        val activity = create(savedState).get()

        assertRestoreFailure(activity)
    }

    private fun assertExpiredSnapshotFinishes(registerInstance: Boolean) {
        if (!registerInstance) replaceEnvironment(registerInstance = false)
        val expiredFlowData = environment.flowData(expiresAtElapsedRealtime = SystemClock.elapsedRealtime())
        val savedState = environment.savedState(environment.restoredOtpStep(expiredFlowData))

        val activity = create(savedState).get()

        assertRestoreFailure(activity)
    }

    private fun launch(
        savedState: Bundle,
        flowType: OwnIdNativeFlowType = OwnIdNativeFlowType.LOGIN,
    ): ActivityController<OwnIdActivity> = trackedController(flowType).setup(savedState).also {
        idleMainLooper()
    }

    private fun create(
        savedState: Bundle,
        flowType: OwnIdNativeFlowType = OwnIdNativeFlowType.LOGIN,
    ): ActivityController<OwnIdActivity> = trackedController(flowType).create(savedState)

    private fun trackedController(flowType: OwnIdNativeFlowType): ActivityController<OwnIdActivity> =
        trackedController(environment.intent(flowType))

    private fun trackedController(intent: Intent): ActivityController<OwnIdActivity> =
        Robolectric.buildActivity(OwnIdActivity::class.java, intent).also(controllers::add)

    private fun replaceEnvironment(registerInstance: Boolean) {
        environment.close()
        environment = NativeFlowTestEnvironment(withServerConfiguration = false, registerInstance = registerInstance)
    }

    private fun saveInstanceState(controller: ActivityController<OwnIdActivity>): Bundle = Bundle().also {
        controller.pause().stop().saveInstanceState(it)
    }

    private fun viewModel(activity: OwnIdActivity): OwnIdNativeFlowViewModelInt =
        ViewModelProvider(activity)[OwnIdNativeFlowViewModelInt::class.java]

    private fun otpStep(activity: OwnIdActivity): OtpAuthStep =
        requireNotNull(viewModel(activity).ownIdFlowStep.value as? OtpAuthStep)

    private fun otpFragments(activity: OwnIdActivity): List<OtpAuthStepUI> =
        activity.supportFragmentManager.fragments.filterIsInstance<OtpAuthStepUI>()

    private fun otpEditText(activity: OwnIdActivity): EditText =
        requireNotNull(otpFragments(activity).single().view?.findViewById(R.id.com_ownid_sdk_internal_ui_otp))

    private fun sendUiFailure(activity: OwnIdActivity, message: String) {
        activity.supportFragmentManager.setFragmentResult(
            OwnIdActivity.KEY_RESULT_UI_ERROR,
            Bundle(1).apply {
                putSerializable(OwnIdActivity.KEY_RESULT_UI_ERROR, IllegalStateException(message))
            },
        )
        idleMainLooper()
    }

    @Suppress("DEPRECATION")
    private fun result(activity: OwnIdActivity): Result<*> =
        requireNotNull(shadowOf(activity).resultIntent.getSerializableExtra(OwnIdActivity.KEY_RESULT) as? Result<*>)

    private fun assertRestoreFailure(activity: OwnIdActivity) {
        assertThat(activity.isFinishing).isTrue()
        val error = result(activity).exceptionOrNull()
        assertThat(error).isInstanceOf(OwnIdFlowCanceled::class.java)
        assertThat((error as OwnIdFlowCanceled).step).isEqualTo(OwnIdFlowCanceled.RESULT_PENDING)
        assertThat(error.message).isEqualTo("OwnID flow was interrupted before completion")
    }

    private fun assertCoreLookupFailure(activity: OwnIdActivity) {
        assertThat(activity.isFinishing).isTrue()
        assertThat(result(activity).exceptionOrNull()).isInstanceOf(OwnIdException::class.java)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleMainLooper(duration: Long, unit: TimeUnit) {
        shadowOf(Looper.getMainLooper()).idleFor(duration, unit)
    }

    private companion object {
        private const val PARTIAL_OTP = "12"
        private const val FULL_OTP = "1234"
        private const val SENTINEL_REDIRECT_URI = "sentinel://restored-flow"
        private const val SENTINEL_LANGUAGE_TAG = "sentinel-language"
        private const val SENTINEL_VERIFIER = "sentinel-verifier"
        private const val INSTANCE_NAME_KEY = "com.ownid.sdk.internal.intent.KEY_INSTANCE_NAME"
    }
}
