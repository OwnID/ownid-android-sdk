package com.ownid.sdk.internal.nativeflow

import android.os.Handler
import com.google.common.truth.Truth
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCoreImpl
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.locale.OwnIdLocale
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowData
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowLoginId
import com.ownid.sdk.internal.feature.nativeflow.OwnIdNativeFlowType
import io.mockk.every
import io.mockk.mockk
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awaitility.Durations
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.awaitility.kotlin.untilNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class AbstractStepTest {
    private val postJsonData =
        " {\"type\":\"login\",\"loginId\":\"ffg@gg.nb\",\"supportsFido2\":true,\"qr\":false,\"sessionChallenge\":\"J6PJHnLg_m3zSxNzVrTpKvrm0VngZPzpe3DpjngWUA0\"}"

    private val requestBody =
        "{\"url\":\"https://passwordless.dev.ownid.com/sign?q=https%3a%2f%2fybmrs2pxdeazta.server.dev.ownid.com%2fownid%2flQH3_b5WFUSJvFCK8tX-8Q%2fstart\\u0026ll=3\\u0026l=en-US\",\"context\":\"lQH3_b5WFUSJvFCK8tX-8Q\",\"nonce\":\"3cd06e90-516b-4691-8f55-c34674bf629a\",\"expiration\":1200000}"

    private class AbstractStepTest(
        ownIdNativeFlowData: OwnIdNativeFlowData, onNextStep: (com.ownid.sdk.internal.feature.nativeflow.AbstractStep) -> Unit, networkHandler: Handler?
    ) : com.ownid.sdk.internal.feature.nativeflow.AbstractStep(ownIdNativeFlowData, onNextStep, networkHandler)

    private fun onNextStep(abstractStep: com.ownid.sdk.internal.feature.nativeflow.AbstractStep) {}

    private val ownIdCoreMockk = mockk<OwnIdCoreImpl>()
    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val ownIdLocaleServiceMockk = mockk<OwnIdLocaleService>()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var ownIdNativeFlowData: OwnIdNativeFlowData
    private lateinit var abstractStepTest: AbstractStepTest

    @Before
    public fun setUp() {
        every { ownIdCoreMockk.configuration } returns TestDataCore.validConfig
        every { ownIdCoreMockk.okHttpClient } returns okHttpClient
        every { ownIdCoreMockk.localeService } returns ownIdLocaleServiceMockk
        every { ownIdLocaleServiceMockk.currentOwnIdLocale } returns OwnIdLocale.DEFAULT

        TestDataCore.validConfig.setServerConfiguration(TestDataCore.validServerConfig)

        ownIdNativeFlowData = OwnIdNativeFlowData(
            ownIdCoreMockk,
            OwnIdNativeFlowType.LOGIN,
            null,
            OwnIdNativeFlowLoginId.fromString("", TestDataCore.validConfig)
        )
        abstractStepTest = AbstractStepTest(ownIdNativeFlowData, ::onNextStep, null)

        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    public fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    public fun correctRequest_correctResponse() {
        val successResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(requestBody)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference("")

        abstractStepTest.doPostRequest(ownIdNativeFlowData, ownIdUrl, postJsonData) {
            onFailure { throw it }
            onSuccess { responseReference.set(it) }
        }

        await until { responseReference.get().isNotBlank() }

        val request = mockWebServer.takeRequest()

        Truth.assertThat(request.getHeader("User-Agent")).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(request.getHeader("Accept-Language")).isEqualTo(TestDataCore.validLanguage)
        Truth.assertThat(request.getHeader("Cache-Control")).isEqualTo("no-cache, no-store")
        Truth.assertThat(request.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8")
        Truth.assertThat(request.body.readUtf8()).isEqualTo(postJsonData)
        Truth.assertThat(responseReference.get()).isEqualTo(requestBody)
    }

    @Test
    public fun correctRequestWithNoLanguageHeader() {
        val successResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(requestBody)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference("")

        val ownIdNativeFlowData = OwnIdNativeFlowData(ownIdCoreMockk, OwnIdNativeFlowType.LOGIN, null, OwnIdNativeFlowLoginId.fromString("", TestDataCore.validConfig))
        val abstractStepTest = AbstractStepTest(ownIdNativeFlowData, ::onNextStep, null)
        abstractStepTest.doPostRequest(ownIdNativeFlowData, ownIdUrl, postJsonData) {
            onFailure { throw it }
            onSuccess { responseReference.set(it) }
        }

        await until { responseReference.get().isNotBlank() }

        val request = mockWebServer.takeRequest()

        Truth.assertThat(request.getHeader("User-Agent")).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(request.getHeader("Accept-Language")).isEqualTo("en")
        Truth.assertThat(request.getHeader("Cache-Control")).isEqualTo("no-cache, no-store")
        Truth.assertThat(request.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8")
        Truth.assertThat(request.body.readUtf8()).isEqualTo(postJsonData)
        Truth.assertThat(responseReference.get()).isEqualTo(requestBody)
    }

    @Test
    public fun redirectResponse_returnError() {
        val successResponse = MockResponse()
            .setResponseCode(301)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference<Throwable?>(null)

        abstractStepTest.doPostRequest(ownIdNativeFlowData, ownIdUrl, postJsonData) {
            onFailure { responseReference.set(it) }
        }

        await untilNotNull { responseReference.get() }

        Truth.assertThat(responseReference.get()).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(responseReference.get()!!.message).startsWith("Server response (")
        Truth.assertThat(responseReference.get()!!.message).endsWith("): 301 Redirection")
    }

    @Test
    public fun response404_returnError() {
        val successResponse = MockResponse()
            .setResponseCode(404)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference<Throwable?>(null)

        abstractStepTest.doPostRequest(ownIdNativeFlowData, ownIdUrl, postJsonData) {
            onFailure { responseReference.set(it) }
        }

        await untilNotNull { responseReference.get() }

        Truth.assertThat(responseReference.get()).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(responseReference.get()!!.message).startsWith("Server response (")
        Truth.assertThat(responseReference.get()!!.message).endsWith("): 404 Client Error")
    }

    @Test
    public fun correctRequest_slowNetwork_correctResponse() {
        val successResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(requestBody)
            .throttleBody(48, 1, TimeUnit.SECONDS)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference("")

        abstractStepTest.doPostRequest(ownIdNativeFlowData, ownIdUrl, postJsonData) {
            onSuccess { responseReference.set(it) }
            onFailure { throw it }
        }

        await until { responseReference.get().isNotBlank() }

        val request = mockWebServer.takeRequest()

        Truth.assertThat(request.getHeader("User-Agent")).isEqualTo(TestDataCore.validUserAgent)
        Truth.assertThat(request.getHeader("Accept-Language")).isEqualTo(TestDataCore.validLanguage)
        Truth.assertThat(request.getHeader("Cache-Control")).isEqualTo("no-cache, no-store")
        Truth.assertThat(request.getHeader("Content-Type")).isEqualTo("application/json; charset=utf-8")
        Truth.assertThat(request.body.readUtf8()).isEqualTo(postJsonData)
        Truth.assertThat(responseReference.get()).isEqualTo(requestBody)
    }

    @Test
    public fun correctRequest_verySlowNetwork_timeoutException() {
        val successResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(requestBody)
            .throttleBody(16, 3, TimeUnit.SECONDS)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference<Throwable?>(null)


        abstractStepTest.doPostRequest(ownIdNativeFlowData, ownIdUrl, postJsonData) {
            onFailure { responseReference.set(it) }
        }

        await.atMost(Durations.ONE_MINUTE) untilNotNull { responseReference.get() }

        Truth.assertThat(responseReference.get()).isInstanceOf(OwnIdException::class.java)
        Truth.assertThat(responseReference.get()!!.message).startsWith("Request fail ")
        Truth.assertThat(responseReference.get()!!.cause).isInstanceOf(InterruptedIOException::class.java)
    }
}