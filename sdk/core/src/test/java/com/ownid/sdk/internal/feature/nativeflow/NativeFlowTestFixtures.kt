package com.ownid.sdk.internal.feature.nativeflow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnId
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.OwnIdInstance
import com.ownid.sdk.OwnIdLoginType
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.internal.component.config.OwnIdConfigurationService
import com.ownid.sdk.internal.component.events.OwnIdInternalEventsService
import com.ownid.sdk.internal.component.locale.OwnIdLocale
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import com.ownid.sdk.internal.component.repository.OwnIdRepositoryService
import com.ownid.sdk.internal.feature.nativeflow.steps.otp.OtpAuthStep
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.util.UUID
import java.util.concurrent.TimeUnit

@OptIn(InternalOwnIdAPI::class)
internal class NativeFlowTestEnvironment(
    withServerConfiguration: Boolean = true,
    registerInstance: Boolean = true,
) : AutoCloseable {
    internal val mockWebServer = MockWebServer().apply { start() }
    internal val instanceName = InstanceName("NativeFlowTest-${UUID.randomUUID()}")
    internal val applicationContext: Context = ApplicationProvider.getApplicationContext()

    internal val configuration = Configuration(
        TestDataCore.validAppId,
        "${TestDataCore.validEnv}.",
        TestDataCore.validRegion,
        TestDataCore.validRedirectUrl,
        TestDataCore.validVersion,
        TestDataCore.validUserAgent,
        TestDataCore.validPackageName,
        TestDataCore.validHashSet,
        mockWebServer.url("/"),
    ).apply {
        if (withServerConfiguration) setServerConfiguration(TestDataCore.validServerConfig)
    }

    internal val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val localeService = mockk<OwnIdLocaleService>(relaxed = true) {
        every { currentOwnIdLocale } returns OwnIdLocale.DEFAULT
    }
    private val eventsService = mockk<OwnIdInternalEventsService>(relaxed = true)
    private val repository = mockk<OwnIdRepositoryService>(relaxed = true) {
        coEvery { getLoginId() } returns null
    }
    internal val configurationService = mockk<OwnIdConfigurationService>(relaxed = true) {
        every { ensureConfigurationSet(any()) } answers {
            firstArg<OwnIdCallback<Unit>>().invoke(Result.success(Unit))
        }
    }

    internal val ownIdCore = mockk<OwnIdCoreImpl>(relaxed = true) {
        every { instanceName } returns this@NativeFlowTestEnvironment.instanceName
        every { configuration } returns this@NativeFlowTestEnvironment.configuration
        every { applicationContext } returns this@NativeFlowTestEnvironment.applicationContext
        every { correlationId } returns "test-correlation-id"
        every { okHttpClient } returns this@NativeFlowTestEnvironment.okHttpClient
        every { this@mockk.eventsService } returns this@NativeFlowTestEnvironment.eventsService
        every { this@mockk.localeService } returns this@NativeFlowTestEnvironment.localeService
        every { this@mockk.repository } returns this@NativeFlowTestEnvironment.repository
        every { this@mockk.configurationService } returns this@NativeFlowTestEnvironment.configurationService
    }

    private val ownIdInstance = object : OwnIdInstance {
        override val ownIdCore = this@NativeFlowTestEnvironment.ownIdCore
        override val ownIdIntegration = null
    }

    init {
        if (registerInstance) registerInstance()
    }

    internal fun registerInstance() {
        OwnId.putInstance(ownIdInstance)
    }

    internal fun intent(flowType: OwnIdNativeFlowType = OwnIdNativeFlowType.LOGIN): Intent =
        OwnIdNativeFlowFeature.createIntent(
            applicationContext,
            instanceName,
            flowType,
            OwnIdLoginType.Standard,
            TestDataCore.validEmail,
        )

    internal fun flowData(
        flowType: OwnIdNativeFlowType = OwnIdNativeFlowType.LOGIN,
        expiresAtElapsedRealtime: Long = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(5),
        enableRegistrationFromLogin: Boolean = configuration.isServerConfigurationSet &&
                configuration.server.enableRegistrationFromLogin,
        redirectUri: String = configuration.getRedirectUri(),
        languageTag: String = OwnIdLocale.DEFAULT.serverLanguageTag,
        restoredVerifier: String? = null,
    ): OwnIdNativeFlowData = OwnIdNativeFlowData(
        ownIdCore = ownIdCore,
        flowType = flowType,
        loginType = OwnIdLoginType.Standard,
        loginId = OwnIdNativeFlowLoginId.restore("email", TestDataCore.validEmail),
        restoredVerifier = restoredVerifier,
        enableRegistrationFromLogin = enableRegistrationFromLogin,
        redirectUri = redirectUri,
        languageTag = languageTag,
    ).apply {
        context = "test-context"
        this.expiresAtElapsedRealtime = expiresAtElapsedRealtime
        statusFinalUrl = mockWebServer.url("/status/final")
    }

    internal fun otpData(
        otpLength: Int = 4,
        verificationType: OtpAuthStep.VerificationType = OtpAuthStep.VerificationType.Email,
        operationType: OtpAuthStep.OperationType = OtpAuthStep.OperationType.Sign,
    ) = OtpAuthStep.Data(
        mockWebServer.url("/otp"),
        mockWebServer.url("/restart"),
        mockWebServer.url("/resend"),
        otpLength,
        verificationType,
        operationType,
    )

    internal fun restoredOtpStep(
        flowData: OwnIdNativeFlowData = flowData(),
        data: OtpAuthStep.Data = otpData(),
        onNextStep: (AbstractStep) -> Unit = {},
    ): OtpAuthStep = OtpAuthStep.Factory.restore(flowData, data, onNextStep)

    internal fun savedState(step: OtpAuthStep): Bundle = Bundle().apply {
        putBundle(OtpRecoveryState.BUNDLE_KEY, parcelCopy(OtpRecoveryState.from(step).toBundle()))
    }

    override fun close() {
        okHttpClient.dispatcher.cancelAll()
        mockWebServer.shutdown()
    }
}

internal fun parcelCopy(bundle: Bundle): Bundle {
    val parcel = Parcel.obtain()
    return try {
        bundle.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        requireNotNull(parcel.readBundle(NativeFlowTestEnvironment::class.java.classLoader))
    } finally {
        parcel.recycle()
    }
}
