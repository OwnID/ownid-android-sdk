package com.ownid.sdk.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Network
import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: NET-TRANSPORT-010, NET-TRANSPORT-020, NET-TRANSPORT-030, NET-TRANSPORT-040
internal class NetworkImplRuntimeContractTest {

    @Test
    fun executeMapsSuccessfulHttpResponseFromOkHttpTransport() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setHeader("X-OwnID-Test", "success-header")
                    .setBody("""{"ok":true}""")
            )
            val request = Request.Builder()
                .url(server.url("/v1/success?source=runtime"))
                .post("""{"request":true}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = network().execute(request)

            assertThat(response).isInstanceOf(Network.Response.Success::class.java)
            response as Network.Response.Success
            assertThat(response.url).isEqualTo(request.url)
            assertThat(response.code).isEqualTo(201)
            assertThat(response.body).isEqualTo("""{"ok":true}""")
            assertThat(response.headers["X-OwnID-Test"]).containsExactly("success-header")
            assertThat(server.takeRequest().path).isEqualTo("/v1/success?source=runtime")
        }
    }

    @Test
    fun executeMapsNonSuccessfulHttpResponseFromOkHttpTransport() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setHeader("X-OwnID-Test", "http-error-header")
                    .setBody("""{"error":"conflict"}""")
            )
            val request = Request.Builder().url(server.url("/v1/conflict")).build()

            val response = network().execute(request)

            assertThat(response).isInstanceOf(Network.Response.Fail.HttpError::class.java)
            response as Network.Response.Fail.HttpError
            assertThat(response.url).isEqualTo(request.url)
            assertThat(response.statusCode).isEqualTo(409)
            assertThat(response.body).isEqualTo("""{"error":"conflict"}""")
            assertThat(response.headers["X-OwnID-Test"]).containsExactly("http-error-header")
            assertThat(server.takeRequest().path).isEqualTo("/v1/conflict")
        }
    }

    @Test
    fun executeMapsTransportFailureFromOkHttpTransport() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val request = Request.Builder().url(server.url("/v1/disconnect")).build()

            val response = network().execute(request)

            assertThat(response).isInstanceOf(Network.Response.Fail.NetworkError::class.java)
            response as Network.Response.Fail.NetworkError
            assertThat(response.url).isEqualTo(request.url)
            assertThat(response.error).hasMessageThat().isNotEmpty()
        }
    }

    @Test
    fun executeMapsTimeoutAsNetworkErrorAndDoesNotRetryIt() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val request = Request.Builder().url(server.url("/v1/timeout")).build()
            val timeoutClient = OkHttpClient.Builder()
                .readTimeout(50, TimeUnit.MILLISECONDS)
                .build()

            val response = network(timeoutClient, retryConfig = retryConfig(retries = 1)).execute(request)

            assertThat(response).isInstanceOf(Network.Response.Fail.NetworkError::class.java)
            response as Network.Response.Fail.NetworkError
            assertThat(response.url).isEqualTo(request.url)
            assertThat(response.error).isInstanceOf(InterruptedIOException::class.java)
            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/v1/timeout")
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun executeRetriesRecoverableTransportFailureAndReturnsLaterSuccess() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setResponseCode(202).setBody("""{"retried":true}"""))
            val traceSeed = "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01"
            val request = Request.Builder()
                .url(server.url("/v1/retry"))
                .header(Network.Request.Header.TRACE_PARENT, traceSeed)
                .build()

            val response = network(retryConfig = retryConfig(retries = 1, initialDelay = 10)).execute(request)

            assertThat(response).isInstanceOf(Network.Response.Success::class.java)
            response as Network.Response.Success
            assertThat(response.code).isEqualTo(202)
            assertThat(response.body).isEqualTo("""{"retried":true}""")

            val firstRequest = server.takeRequest(1, TimeUnit.SECONDS)
            val secondRequest = server.takeRequest(1, TimeUnit.SECONDS)
            assertThat(firstRequest?.path).isEqualTo("/v1/retry")
            assertThat(secondRequest?.path).isEqualTo("/v1/retry")
            val firstTraceparent = firstRequest!!.getHeader(Network.Request.Header.TRACE_PARENT)
            val secondTraceparent = secondRequest!!.getHeader(Network.Request.Header.TRACE_PARENT)
            assertThat(firstTraceparent).matches("""00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-[0-9a-f]{16}-01""")
            assertThat(secondTraceparent).matches("""00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-[0-9a-f]{16}-01""")
            assertThat(secondTraceparent).isNotEqualTo(firstTraceparent)
        }
    }

    @Test
    fun executeExhaustsRecoverableTransportRetriesAndReturnsFinalNetworkError() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val request = Request.Builder().url(server.url("/v1/retry-exhausted")).build()

            val response = network(retryConfig = retryConfig(retries = 1, initialDelay = 10)).execute(request)

            assertThat(response).isInstanceOf(Network.Response.Fail.NetworkError::class.java)
            response as Network.Response.Fail.NetworkError
            assertThat(response.url).isEqualTo(request.url)
            assertThat(response.error).hasMessageThat().isNotEmpty()
            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/v1/retry-exhausted")
            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/v1/retry-exhausted")
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    @Test
    fun executeDoesNotRetryHttpErrorResponses() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"unavailable"}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val request = Request.Builder().url(server.url("/v1/http-error-no-retry")).build()

            val response = network(retryConfig = retryConfig(retries = 1, initialDelay = 10)).execute(request)

            assertThat(response).isInstanceOf(Network.Response.Fail.HttpError::class.java)
            response as Network.Response.Fail.HttpError
            assertThat(response.statusCode).isEqualTo(503)
            assertThat(response.body).isEqualTo("""{"error":"unavailable"}""")
            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/v1/http-error-no-retry")
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun cancellationDuringActiveReadCancelsOkHttpCall() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val cancelProbe = CallCancelProbe()
            val client = OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .eventListener(cancelProbe)
                .build()
            val request = Request.Builder().url(server.url("/v1/active-read")).build()
            val response = async { network(client).execute(request) }
            runCurrent()

            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/v1/active-read")

            response.cancel()
            response.join()

            assertThat(cancelProbe.awaitCanceled()).isTrue()
            assertThat(response.isCancelled).isTrue()
        }
    }

    @Test
    fun cancellationDuringRetryDelayPreventsNextAttempt() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setResponseCode(204))
            val failureProbe = CallFailureProbe()
            val client = OkHttpClient.Builder()
                .eventListener(failureProbe)
                .build()
            val request = Request.Builder().url(server.url("/v1/retry-delay")).build()
            val response = async {
                network(client, retryConfig = retryConfig(retries = 1, initialDelay = 60_000)).execute(request)
            }
            runCurrent()

            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/v1/retry-delay")
            assertThat(failureProbe.awaitFailure()).isTrue()
            runCurrent()

            response.cancel()
            response.join()
            advanceTimeBy(60_000.milliseconds)
            runCurrent()

            assertThat(response.isCancelled).isTrue()
            assertThat(server.requestCount).isEqualTo(1)
        }
    }

    @Test
    fun defaultMetadataHeadersReachRecordedOkHttpRequestAndMergeCorrelationBaggage() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            val traceSeed = "00-11111111111111111111111111111111-2222222222222222-01"
            val request = Request.Builder()
                .url(server.url("/v1/metadata"))
                .header(Network.Request.Header.BAGGAGE, "tenant=example")
                .header(Network.Request.Header.TRACE_PARENT, traceSeed)
                .build()

            val response = network(defaultMetadataClient()).execute(request)

            assertThat(response).isInstanceOf(Network.Response.Success::class.java)
            val recordedRequest = server.takeRequest()
            assertThat(recordedRequest.getHeader(Network.Request.Header.USER_AGENT)).isEqualTo("OwnID-Test/1.0")
            assertThat(recordedRequest.getHeader(Network.Request.Header.ACCEPT_LANGUAGE)).isEqualTo("en-US,fr")
            assertThat(recordedRequest.getHeader(Network.Request.Header.OWNID_APP_URL)).isEqualTo("app123.server.ownid.com")
            assertThat(recordedRequest.getHeader(Network.Request.Header.BAGGAGE)).isEqualTo("tenant=example,sdk.correlation_id=correlation-1")
            assertThat(recordedRequest.getHeader(Network.Request.Header.TRACE_PARENT))
                .matches("""00-11111111111111111111111111111111-[0-9a-f]{16}-01""")
        }
    }

    @Test
    fun defaultMetadataHeadersDoNotDuplicateExistingCorrelationBaggageInRecordedOkHttpRequest() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            val request = Request.Builder()
                .url(server.url("/v1/metadata-existing-correlation"))
                .header(Network.Request.Header.BAGGAGE, "tenant=example,sdk.correlation_id=existing")
                .build()

            val response = network(defaultMetadataClient()).execute(request)

            assertThat(response).isInstanceOf(Network.Response.Success::class.java)
            val recordedRequest = server.takeRequest()
            assertThat(recordedRequest.getHeader(Network.Request.Header.BAGGAGE)).isEqualTo("tenant=example,sdk.correlation_id=existing")
        }
    }

    private fun network(
        okHttpClient: OkHttpClient = OkHttpClient(),
        retryConfig: Network.Request.RetryConfig? = null
    ): Network = NetworkImpl(okHttpClient = okHttpClient, retryConfig = retryConfig)

    private fun retryConfig(retries: Int, initialDelay: Long = 250): Network.Request.RetryConfig =
        Network.Request.RetryConfig(retries = retries, initialDelay = initialDelay, factor = 2.0, maxDelay = initialDelay)

    private fun defaultMetadataClient(): OkHttpClient =
        DefaultOkHttpClientFactory.create(
            localInfo = localInfo(),
            languageTagsProvider = languageTagsProvider(LanguageTag("en", "US"), LanguageTag("fr", "")),
            appUrlHeaderValue = "app123.server.ownid.com",
            ownIdLogger = null
        ).newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
            .build()

    private fun localInfo(): LocalInfo = object : LocalInfo {
        override val modules: List<Pair<String, String>> = emptyList()
        override val packageName: String = "com.example.app"
        override val appVersion: String = "1.0"
        override val userAgent: String = "OwnID-Test/1.0"
        override val correlationId: String = "correlation-1"
        override val isDebuggable: Boolean = false
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean? = null
        override val isFaceHardwarePresent: Boolean? = null
        override val isIrisHardwarePresent: Boolean? = null
        override val isStrongBiometricEnabled: Boolean? = null
    }

    private fun languageTagsProvider(vararg tags: LanguageTag): LanguageTagsProvider = object : LanguageTagsProvider {
        override val languageTagsFlow: StateFlow<List<LanguageTag>> = MutableStateFlow(tags.toList())

        override fun setLanguageTags(tags: List<String>) = Unit
    }

    private class CallCancelProbe : EventListener() {
        private val canceled = CountDownLatch(1)

        override fun canceled(call: Call) {
            canceled.countDown()
        }

        fun awaitCanceled(): Boolean = canceled.await(1, TimeUnit.SECONDS)
    }

    private class CallFailureProbe : EventListener() {
        private val failed = CountDownLatch(1)

        override fun callFailed(call: Call, ioe: IOException) {
            failed.countDown()
        }

        fun awaitFailure(): Boolean = failed.await(1, TimeUnit.SECONDS)
    }
}
