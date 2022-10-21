package com.ownid.sdk.internal

import com.google.common.truth.Truth
import com.ownid.sdk.Configuration
import com.ownid.sdk.InstanceName
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.RegistrationParameters
import com.ownid.sdk.TestDataCore
import com.ownid.sdk.exception.OwnIdException
import okhttp3.ConnectionSpec
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

@androidx.annotation.OptIn(InternalOwnIdAPI::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
public class NetworkHelperTest {
    private val postJsonData =
        "{\"type\":\"login\",\"qr\":false,\"partial\":true,\"language\":\"en\",\"sessionChallenge\":\"tYpclcFFrn2L8LJ6y4zshKfOUZLmEJ_dtwci_cxBH8I\"}"

    private val requestBody =
        "{\"url\":\"https://sign.dev.ownid.com/sign?q=gigya.server.dev.ownid.com%2fownid%2f9dodQ6PbIESNBXrJ1-r_8A%2fstart\\u0026ll=3\\u0026l=en\",\"context\":\"9dodQ6PbIESNBXrJ1-r_8A\",\"nonce\":\"0e4fc819-39b8-47c5-969e-6b53a3029cd2\",\"expiration\":600000,\"config\":{\"magicLink\":false,\"logLevel\":\"3\"}}"

    private class OwnIdCore(instanceName: InstanceName, configuration: Configuration) :
        OwnIdCoreImpl(instanceName, configuration) {
        override fun register(
            email: String, params: RegistrationParameters?, ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>
        ) {

        }

        override fun login(ownIdResponse: OwnIdResponse, callback: OwnIdCallback<Unit>) {
        }
    }

    private lateinit var mockWebServer: MockWebServer
    private lateinit var ownIdCore: OwnIdCore

    @Before
    public fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        ownIdCore = OwnIdCore(TestDataCore.validInstanceName, TestDataCore.validServerConfig)

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

        NetworkHelper.getInstance(ConnectionSpec.CLEARTEXT, null)
            .doPostJsonRequest(ownIdCore, TestDataCore.validLanguage, ownIdUrl, postJsonData) {
                onFailure { throw it }
                onSuccess { responseReference.set(it) }
            }

        await until { responseReference.get().isNotBlank() }

        val request = mockWebServer.takeRequest()

        Truth.assertThat(request.getHeader("User-Agent"))
            .isEqualTo(TestDataCore.validUserAgent)

        Truth.assertThat(request.getHeader("Accept-Language"))
            .isEqualTo(TestDataCore.validLanguage)

        Truth.assertThat(request.getHeader("Cache-Control"))
            .isEqualTo("no-cache, no-store")

        Truth.assertThat(request.getHeader("Content-Type"))
            .isEqualTo("application/json; charset=utf-8")

        Truth.assertThat(request.body.readUtf8())
            .isEqualTo(postJsonData)

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

        NetworkHelper.getInstance(ConnectionSpec.CLEARTEXT, null)
            .doPostJsonRequest(ownIdCore, "", ownIdUrl, postJsonData) {
                onFailure { throw it }
                onSuccess { responseReference.set(it) }
            }

        await until { responseReference.get().isNotBlank() }

        val request = mockWebServer.takeRequest()

        Truth.assertThat(request.getHeader("User-Agent"))
            .isEqualTo(TestDataCore.validUserAgent)

        Truth.assertThat(request.getHeader("Accept-Language"))
            .isEqualTo("en")

        Truth.assertThat(request.getHeader("Cache-Control"))
            .isEqualTo("no-cache, no-store")

        Truth.assertThat(request.getHeader("Content-Type"))
            .isEqualTo("application/json; charset=utf-8")

        Truth.assertThat(request.body.readUtf8())
            .isEqualTo(postJsonData)

        Truth.assertThat(responseReference.get()).isEqualTo(requestBody)
    }

    @Test
    public fun redirectResponse_returnError() {
        val successResponse = MockResponse()
            .setResponseCode(301)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference<Throwable?>(null)

        NetworkHelper.getInstance(ConnectionSpec.CLEARTEXT, null)
            .doPostJsonRequest(ownIdCore, TestDataCore.validLanguage, ownIdUrl, postJsonData) {
                onFailure { responseReference.set(it) }
            }

        await untilNotNull { responseReference.get() }

        Truth.assertThat(responseReference.get())
            .isInstanceOf(OwnIdException::class.java)

        Truth.assertThat(responseReference.get()!!.message)
            .isEqualTo("Server response: 301 - Redirection")
    }

    @Test
    public fun response404_returnError() {
        val successResponse = MockResponse()
            .setResponseCode(404)

        mockWebServer.enqueue(successResponse)

        val ownIdUrl = mockWebServer.url("/anytesturl")

        val responseReference = AtomicReference<Throwable?>(null)

        NetworkHelper.getInstance(ConnectionSpec.CLEARTEXT, null)
            .doPostJsonRequest(ownIdCore, TestDataCore.validLanguage, ownIdUrl, postJsonData) {
                onFailure { responseReference.set(it) }
            }

        await untilNotNull { responseReference.get() }

        Truth.assertThat(responseReference.get())
            .isInstanceOf(OwnIdException::class.java)

        Truth.assertThat(responseReference.get()!!.message)
            .isEqualTo("Server response: 404 - Client Error")
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

        NetworkHelper.getInstance(ConnectionSpec.CLEARTEXT, null)
            .doPostJsonRequest(ownIdCore, TestDataCore.validLanguage, ownIdUrl, postJsonData) {
                onSuccess { responseReference.set(it) }
                onFailure { throw it }
            }

        await until { responseReference.get().isNotBlank() }

        val request = mockWebServer.takeRequest()

        Truth.assertThat(request.getHeader("User-Agent"))
            .isEqualTo(TestDataCore.validUserAgent)

        Truth.assertThat(request.getHeader("Accept-Language"))
            .isEqualTo(TestDataCore.validLanguage)

        Truth.assertThat(request.getHeader("Cache-Control"))
            .isEqualTo("no-cache, no-store")

        Truth.assertThat(request.getHeader("Content-Type"))
            .isEqualTo("application/json; charset=utf-8")

        Truth.assertThat(request.body.readUtf8())
            .isEqualTo(postJsonData)

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

        NetworkHelper.getInstance(ConnectionSpec.CLEARTEXT, null)
            .doPostJsonRequest(ownIdCore, TestDataCore.validLanguage, ownIdUrl, postJsonData) {
                onFailure { responseReference.set(it) }
            }

        await.atMost(Durations.ONE_MINUTE) untilNotNull { responseReference.get() }

        Truth.assertThat(responseReference.get())
            .isInstanceOf(OwnIdException::class.java)

        Truth.assertThat(responseReference.get()!!.message).startsWith("Request fail ")

        Truth.assertThat(responseReference.get()!!.cause)
            .isInstanceOf(InterruptedIOException::class.java)
    }
}