package com.ownid.sdk.internal.feature.nativeflow

import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.feature.nativeflow.steps.InitStep
import com.ownid.sdk.internal.feature.nativeflow.steps.idcollect.IdCollectStep
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import io.mockk.every
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.net.HttpURLConnection
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class OtpAuthStepRecoveryTest {
    private lateinit var environment: NativeFlowTestEnvironment

    @Before
    fun setUp() {
        environment = NativeFlowTestEnvironment()
    }

    @After
    fun tearDown() {
        environment.close()
    }

    @Test
    fun restoredWaitingStep_isRestorableUntilExpiration() {
        val activeStep = environment.restoredOtpStep()
        val expiredStep = environment.restoredOtpStep(
            flowData = environment.flowData(expiresAtElapsedRealtime = SystemClock.elapsedRealtime()),
        )

        assertThat(activeStep.isRestorable).isTrue()
        assertThat(expiredStep.isRestorable).isFalse()
    }

    @Test
    fun completeOtp_blocksRecoveryWhileRequestIsInFlight() {
        environment.mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val step = environment.restoredOtpStep()

        step.onOtp("1234")
        step.onOtp("5678")

        assertThat(step.state.value?.isBusy).isTrue()
        assertThat(step.state.value?.otp).isEqualTo("1234")
        assertThat(step.isRestorable).isFalse()
    }

    @Test
    fun recoverableOtpError_reEnablesRecovery() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(errorResponse(OwnIdNativeFlowError.CodeServer.WRONG_CODE, flowFinished = false)),
        )
        val step = environment.restoredOtpStep()

        runAndAwaitNetwork { step.onOtp("1234") }

        assertThat(step.state.value?.isBusy).isFalse()
        assertThat((step.state.value?.error as OwnIdNativeFlowError).errorCode)
            .isEqualTo(OwnIdNativeFlowError.CodeServer.WRONG_CODE)
        assertThat(step.isRestorable).isTrue()
    }

    @Test
    fun ambiguousOtpResponse_keepsRecoveryBlocked() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("{}"),
        )
        val step = environment.restoredOtpStep()

        runAndAwaitNetwork { step.onOtp("1234") }

        assertThat(step.state.value?.isBusy).isFalse()
        assertThat(step.state.value?.error).isNotNull()
        assertThat(step.state.value?.error).isNotInstanceOf(OwnIdNativeFlowError::class.java)
        assertThat(step.isRestorable).isFalse()
    }

    @Test
    fun wrongCodeLimitReached_isTerminal() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(errorResponse(OwnIdNativeFlowError.CodeServer.WRONG_CODE_LIMIT_REACHED, flowFinished = true)),
        )
        val step = environment.restoredOtpStep()

        runAndAwaitNetwork { step.onOtp("1234") }

        assertThat(step.state.value?.isBusy).isFalse()
        val error = step.state.value?.error as OwnIdNativeFlowError
        assertThat(error.errorCode).isEqualTo(OwnIdNativeFlowError.CodeServer.WRONG_CODE_LIMIT_REACHED)
        assertThat(error.flowFinished).isTrue()
        assertThat(step.state.value?.notYouVisible).isFalse()
        assertThat(step.isRestorable).isFalse()
    }

    @Test
    fun successfulOtpTransition_blocksRecovery() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(otpStepResponse()),
        )
        val nextStep = arrayListOf<AbstractStep>()
        val step = environment.restoredOtpStep(onNextStep = nextStep::add)

        runAndAwaitNetwork { step.onOtp("1234") }

        assertThat(step.isRestorable).isFalse()
        assertThat(nextStep).hasSize(1)
    }

    @Test
    fun resend_blocksRecoveryUntilSuccessfulResponse() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(otpStepResponse()),
        )
        val step = environment.restoredOtpStep()

        val networkIdle = CountDownLatch(1)
        environment.okHttpClient.dispatcher.idleCallback = Runnable { networkIdle.countDown() }
        step.onResend()
        assertThat(step.state.value?.isBusy).isTrue()
        assertThat(step.isRestorable).isFalse()

        assertThat(networkIdle.await(5, TimeUnit.SECONDS)).isTrue()
        environment.okHttpClient.dispatcher.idleCallback = null
        ShadowLooper.runUiThreadTasks()

        assertThat(step.state.value?.error).isNull()
        assertThat(step.isRestorable).isTrue()
    }

    @Test
    fun notYouTerminalTransition_blocksRecovery() {
        val nextStep = arrayListOf<AbstractStep>()
        val step = environment.restoredOtpStep(onNextStep = nextStep::add)

        step.onNotYou()
        ShadowLooper.runUiThreadTasks()

        assertThat(step.state.value?.done).isTrue()
        assertThat(step.isRestorable).isFalse()
        assertThat(nextStep).hasSize(1)
    }

    @Test
    fun notYou_waitsForConfigurationAndKeepsSafeOtpRestorable() {
        val configurationCallbacks = captureConfigurationCallbacks()
        val nextSteps = arrayListOf<AbstractStep>()
        val step = environment.restoredOtpStep(
            flowData = environment.flowData(enableRegistrationFromLogin = true),
            onNextStep = nextSteps::add,
        )

        step.onNotYou()

        assertThat(step.state.value?.isBusy).isTrue()
        assertThat(step.isRestorable).isTrue()
        assertThat(environment.mockWebServer.requestCount).isEqualTo(0)

        configurationCallbacks.removeFirst().invoke(Result.failure(OwnIdException("Configuration unavailable")))

        assertThat(step.state.value?.isBusy).isFalse()
        assertThat(step.isRestorable).isTrue()
        assertThat(environment.mockWebServer.requestCount).isEqualTo(0)

        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(idCollectStepResponse()),
        )
        step.onNotYou()
        runAndAwaitNetwork { configurationCallbacks.removeFirst().invoke(Result.success(Unit)) }

        assertThat(nextSteps.single()).isInstanceOf(IdCollectStep::class.java)
        assertThat(step.state.value?.done).isTrue()
        assertThat(step.isRestorable).isFalse()
    }

    @Test
    fun notYou_keepsBlockedOtpNonRestorableWhileWaitingForConfiguration() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("{}"),
        )
        val step = environment.restoredOtpStep(
            flowData = environment.flowData(enableRegistrationFromLogin = true),
        )
        runAndAwaitNetwork { step.onOtp("1234") }
        assertThat(step.isRestorable).isFalse()
        val requestCountBeforeNotYou = environment.mockWebServer.requestCount
        captureConfigurationCallbacks()

        step.onNotYou()

        assertThat(step.state.value?.isBusy).isTrue()
        assertThat(step.isRestorable).isFalse()
        assertThat(environment.mockWebServer.requestCount).isEqualTo(requestCountBeforeNotYou)
    }

    @Test
    fun notYou_configurationFailurePreservesFreshInitBranchForRetry() {
        environment.mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(errorResponse(OwnIdNativeFlowError.CodeServer.ACCOUNT_NOT_FOUND, flowFinished = true)),
        )
        val configurationCallbacks = captureConfigurationCallbacks()
        val nextSteps = arrayListOf<AbstractStep>()
        val step = environment.restoredOtpStep(
            flowData = environment.flowData(enableRegistrationFromLogin = true),
            onNextStep = nextSteps::add,
        )
        runAndAwaitNetwork { step.onOtp("1234") }
        val requestCountBeforeNotYou = environment.mockWebServer.requestCount

        step.onNotYou()
        configurationCallbacks.removeFirst().invoke(Result.failure(OwnIdException("Configuration unavailable")))

        assertThat(step.state.value?.isBusy).isFalse()
        assertThat(step.state.value?.error).isInstanceOf(OwnIdException::class.java)
        assertThat(step.isRestorable).isFalse()

        step.onNotYou()
        configurationCallbacks.removeFirst().invoke(Result.success(Unit))
        ShadowLooper.runUiThreadTasks()

        assertThat(nextSteps.single()).isInstanceOf(InitStep::class.java)
        assertThat(step.ownIdNativeFlowData.useLoginId).isFalse()
        assertThat(environment.mockWebServer.requestCount).isEqualTo(requestCountBeforeNotYou)
    }

    @Test
    fun notYou_ignoresConfigurationCallbackAfterCancellation() {
        val configurationCallbacks = captureConfigurationCallbacks()
        val nextSteps = arrayListOf<AbstractStep>()
        val step = environment.restoredOtpStep(
            flowData = environment.flowData(enableRegistrationFromLogin = true),
            onNextStep = nextSteps::add,
        )
        step.onNotYou()

        step.ownIdNativeFlowData.canceller.cancel()
        configurationCallbacks.removeFirst().invoke(Result.success(Unit))
        ShadowLooper.runUiThreadTasks()

        assertThat(nextSteps).isEmpty()
        assertThat(environment.mockWebServer.requestCount).isEqualTo(0)
    }

    @Test
    fun notYou_ignoresConfigurationCallbackAfterStepIsDone() {
        val configurationCallbacks = captureConfigurationCallbacks()
        val nextSteps = arrayListOf<AbstractStep>()
        val step = environment.restoredOtpStep(
            flowData = environment.flowData(enableRegistrationFromLogin = true),
            onNextStep = nextSteps::add,
        )
        step.onNotYou()
        step.onCancel("test")
        ShadowLooper.runUiThreadTasks()

        configurationCallbacks.removeFirst().invoke(Result.success(Unit))
        ShadowLooper.runUiThreadTasks()

        assertThat(nextSteps).hasSize(1)
        assertThat(environment.mockWebServer.requestCount).isEqualTo(0)
    }

    private fun captureConfigurationCallbacks(): ArrayDeque<OwnIdCallback<Unit>> =
        ArrayDeque<OwnIdCallback<Unit>>().also { callbacks ->
            every { environment.configurationService.ensureConfigurationSet(any()) } answers {
                callbacks.addLast(firstArg())
            }
        }

    private fun runAndAwaitNetwork(action: () -> Unit) {
        val networkIdle = CountDownLatch(1)
        environment.okHttpClient.dispatcher.idleCallback = Runnable { networkIdle.countDown() }
        action()
        assertThat(networkIdle.await(5, TimeUnit.SECONDS)).isTrue()
        environment.okHttpClient.dispatcher.idleCallback = null
        ShadowLooper.runUiThreadTasks()
    }

    private fun errorResponse(errorCode: String, flowFinished: Boolean): String = JSONObject()
        .put(
            "error",
            JSONObject()
                .put("errorCode", errorCode)
                .put("userMessage", "Test error")
                .put("flowFinished", flowFinished)
                .put("message", "Test error"),
        )
        .toString()

    private fun otpStepResponse(): String = JSONObject()
        .put(
            "step",
            JSONObject()
                .put("type", "linkWithCode")
                .put(
                    "data",
                    JSONObject()
                        .put("url", environment.mockWebServer.url("/otp").toString())
                        .put("restartUrl", environment.mockWebServer.url("/restart").toString())
                        .put("resendUrl", environment.mockWebServer.url("/resend").toString())
                        .put("otpLength", 4)
                        .put("verificationType", "Email"),
                ),
        )
        .toString()

    private fun idCollectStepResponse(): String = JSONObject()
        .put(
            "step",
            JSONObject()
                .put("type", "Starting")
                .put(
                    "data",
                    JSONObject().put("url", environment.mockWebServer.url("/id-collect").toString()),
                ),
        )
        .toString()
}
