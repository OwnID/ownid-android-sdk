package com.ownid.sdk.logger

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.impl.DefaultOkHttpClientFactory
import com.ownid.sdk.capability.impl.NetworkImpl
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InstanceName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ConnectionSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: NET-TRANSPORT-050
internal class SuppressedHttpLoggingRuntimeContractTest {

    @Test
    fun suppressedNetworkRequestSkipsLocalHttpRequestAndResponseLogging(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"normal":true}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"suppressed":true}"""))
            val localLogs = mutableListOf<LogEntry>()
            val okHttpClient = DefaultOkHttpClientFactory.create(
                localInfo = null,
                languageTagsProvider = null,
                appUrlHeaderValue = "app123.server.ownid.com",
                ownIdLogger = recordingLogger(localLogs)
            ).newBuilder()
                .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
                .build()
            val network = NetworkImpl(okHttpClient = okHttpClient, retryConfig = null)

            val normalResponse = network.execute(
                Network.Request.Builder(server.url("/api/normal")).apply {
                    body = """{"normalRequest":true}"""
                }.build()
            )

            assertThat(normalResponse).isInstanceOf(Network.Response.Success::class.java)
            assertThat(server.takeRequestPath()).isEqualTo("/api/normal")
            assertThat(localLogs.any { it.message.startsWith("--> POST") && it.message.contains("/api/normal") }).isTrue()
            assertThat(localLogs.any { it.message.startsWith("<-- 200") && it.message.contains("/api/normal") }).isTrue()

            val logCountBeforeSuppressedRequest = localLogs.size
            val suppressedResponse = network.execute(
                Network.Request.Builder(server.url("/events")).apply {
                    body = """{"diagnostic":true}"""
                    suppressHttpLog()
                }.build()
            )

            assertThat(suppressedResponse).isInstanceOf(Network.Response.Success::class.java)
            assertThat(server.takeRequestPath()).isEqualTo("/events")
            assertThat(localLogs).hasSize(logCountBeforeSuppressedRequest)
        }
    }

    @Test
    fun diagnosticsPostsCarrySuppressedHttpLogTagToAvoidRecursiveHttpLogging(): Unit = runTest {
        val network = RecordingServerNetwork()
        val instanceJob = Job()
        val logger = ServerLogger(
            instanceName = InstanceName("diagnostics-suppression-test"),
            configuration = diagnosticsConfiguration(rootUrl = "https://diagnostics.example/diagnostics/"),
            localInfo = diagnosticsLocalInfo(modules = emptyList()),
            appConfigProvider = ImmediateAppConfigProvider(diagnosticsAppConfig(AppConfig.LogLevel.DEBUG)),
            network = network,
            serialization = SerializationImpl(),
            dispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            ownIdLogger = null
        )

        try {
            runCurrent()
            logger.log(OwnIdLogger.Level.Error, "DiagnosticsSource", "visible diagnostic", cause = null)
            runCurrent()

            assertThat(network.requests).hasSize(1)
            val request = network.requests.single()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/diagnostics/events")
            assertThat(request.tag(Network.Request.NoHttpLogTag::class.java)).isSameInstanceAs(Network.Request.NoHttpLogTag)
            assertThat(request.bodyString()).contains("DiagnosticsSource => visible diagnostic")
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    private fun recordingLogger(entries: MutableList<LogEntry>): OwnIdLogger = object : OwnIdLogger {
        override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
        override val tag: String = "OwnID-Test"

        override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
            entries += LogEntry(level, className, message, cause)
        }
    }

    private fun okhttp3.Request.bodyString(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun MockWebServer.takeRequestPath(): String? = takeRequest(1, TimeUnit.SECONDS)?.path

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val cause: Throwable?
    )

}
