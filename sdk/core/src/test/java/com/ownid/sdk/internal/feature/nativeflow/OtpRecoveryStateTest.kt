package com.ownid.sdk.internal.feature.nativeflow

import android.os.Bundle
import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class OtpRecoveryStateTest {
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
    fun bundleParcelRoundTrip_preservesFlowAndOtpState() {
        val step = environment.restoredOtpStep(
            data = environment.otpData(
                otpLength = 6,
                verificationType = OtpAuthStep.VerificationType.Sms,
                operationType = OtpAuthStep.OperationType.Verify,
            ),
        )

        val restored = OtpRecoveryState.fromBundle(parcelCopy(OtpRecoveryState.from(step).toBundle()))

        assertThat(restored.loginId.localeKey).isEqualTo(step.ownIdNativeFlowData.loginId.localeKey)
        assertThat(restored.loginId.value).isEqualTo(step.ownIdNativeFlowData.loginId.value)
        assertThat(restored.verifier).isEqualTo(step.ownIdNativeFlowData.verifier)
        assertThat(restored.context).isEqualTo(step.ownIdNativeFlowData.context)
        assertThat(restored.statusFinalUrl).isEqualTo(step.ownIdNativeFlowData.statusFinalUrl)
        assertThat(restored.expiresAtElapsedRealtime).isEqualTo(step.ownIdNativeFlowData.expiresAtElapsedRealtime)
        assertThat(restored.enableRegistrationFromLogin).isEqualTo(step.ownIdNativeFlowData.enableRegistrationFromLogin)
        assertThat(restored.redirectUri).isEqualTo(step.ownIdNativeFlowData.redirectUri)
        assertThat(restored.languageTag).isEqualTo(step.ownIdNativeFlowData.languageTag)
        assertThat(restored.otpData.url).isEqualTo(step.data.url)
        assertThat(restored.otpData.restartUrl).isEqualTo(step.data.restartUrl)
        assertThat(restored.otpData.resendUrl).isEqualTo(step.data.resendUrl)
        assertThat(restored.otpData.otpLength).isEqualTo(6)
        assertThat(restored.otpData.verificationType).isEqualTo(OtpAuthStep.VerificationType.Sms)
        assertThat(restored.otpData.operationType).isEqualTo(OtpAuthStep.OperationType.Verify)
    }

    @Test
    fun bundleRoundTrip_preservesEverySupportedLoginIdType() {
        mapOf(
            "email" to "test@example.com",
            "phoneNumber" to "+15555550123",
            "userName" to "test-user",
        ).forEach { (type, value) ->
            val flowData = environment.flowData().apply {
                loginId = OwnIdNativeFlowLoginId.restore(type, value)
            }
            val restored = OtpRecoveryState.fromBundle(
                parcelCopy(OtpRecoveryState.from(environment.restoredOtpStep(flowData = flowData)).toBundle()),
            )

            assertThat(restored.loginId.localeKey).isEqualTo(type)
            assertThat(restored.loginId.value).isEqualTo(value)
        }
    }

    @Test
    fun fromBundle_expiredSnapshot_throws() {
        val bundle = OtpRecoveryState.from(
            environment.restoredOtpStep(
                flowData = environment.flowData(expiresAtElapsedRealtime = SystemClock.elapsedRealtime()),
            ),
        ).toBundle()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            OtpRecoveryState.fromBundle(parcelCopy(bundle))
        }

        assertThat(exception).hasMessageThat().contains("expired")
    }

    @Test
    fun fromBundle_missingRequiredState_throws() {
        val source = OtpRecoveryState.from(environment.restoredOtpStep()).toBundle()

        listOf("enableRegistrationFromLogin", "verifier", "otpUrl").forEach { key ->
            val bundle = parcelCopy(source).apply { remove(key) }
            assertThrows(IllegalArgumentException::class.java) {
                OtpRecoveryState.fromBundle(bundle)
            }
        }
    }

    @Test
    fun fromBundle_invalidOtpState_throws() {
        val source = OtpRecoveryState.from(environment.restoredOtpStep()).toBundle()

        listOf(
            Bundle(source).apply { putInt("otpLength", 0) },
            Bundle(source).apply { putString("otpUrl", "not a URL") },
            Bundle(source).apply { putString("otpVerificationType", "Push") },
            Bundle(source).apply { putString("otpOperationType", "Enroll") },
            Bundle(source).apply { putString("loginIdType", "unknown") },
        ).forEach { bundle ->
            assertThrows(IllegalArgumentException::class.java) {
                OtpRecoveryState.fromBundle(bundle)
            }
        }
    }
}
