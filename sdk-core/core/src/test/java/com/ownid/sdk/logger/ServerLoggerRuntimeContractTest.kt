package com.ownid.sdk.logger

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.impl.DefaultOkHttpClientFactory
import com.ownid.sdk.capability.impl.NetworkImpl
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.EventListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
// Covers: DIAG-RUNTIME-010, DIAG-RUNTIME-020, DIAG-RUNTIME-030
internal class ServerLoggerRuntimeContractTest {

    @Test
    fun waitsForRuntimeConfigAndAppliesUpdatedServerThresholds(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            val appConfigProvider = ControlledAppConfigProvider(initial = diagnosticsAppConfig(AppConfig.LogLevel.WARNING))
            val instanceJob = Job()
            val callCompletionListener = CallCompletionListener()
            val logger = serverLogger(server, appConfigProvider, StandardTestDispatcher(testScheduler), instanceJob, callCompletionListener)

            try {
                logger.log(OwnIdLogger.Level.Warn, "DiagnosticsSource", "queued until config", cause = null)
                runCurrent()

                assertThat(server.requestCount).isEqualTo(0)

                appConfigProvider.completeFetch(diagnosticsAppConfig(AppConfig.LogLevel.WARNING))
                runCurrent()

                assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/diagnostics/events")
                callCompletionListener.awaitCompletion()
                runCurrent()

                appConfigProvider.update(diagnosticsAppConfig(AppConfig.LogLevel.ERROR))
                runCurrent()
                logger.log(OwnIdLogger.Level.Warn, "DiagnosticsSource", "below updated threshold", cause = null)
                runCurrent()

                assertThat(server.requestCount).isEqualTo(1)

                appConfigProvider.update(diagnosticsAppConfig(AppConfig.LogLevel.DEBUG))
                runCurrent()
                logger.log(OwnIdLogger.Level.Debug, "DiagnosticsSource", "enabled by updated threshold", cause = null)
                runCurrent()

                assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.path).isEqualTo("/diagnostics/events")
                callCompletionListener.awaitCompletion()
                runCurrent()
                assertThat(server.requestCount).isEqualTo(2)
            } finally {
                instanceJob.cancel()
                runCurrent()
            }
        }
    }

    @Test
    fun postsDiagnosticsPayloadWithAndroidRuntimeContext(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            val appConfigProvider = ControlledAppConfigProvider(initial = diagnosticsAppConfig(AppConfig.LogLevel.DEBUG))
            appConfigProvider.completeFetch(diagnosticsAppConfig(AppConfig.LogLevel.DEBUG))
            val instanceJob = Job()
            val logger = serverLogger(server, appConfigProvider, StandardTestDispatcher(testScheduler), instanceJob)
            val cause = IllegalStateException("broken diagnostic")

            try {
                runCurrent()
                logger.log(OwnIdLogger.Level.Error, "DiagnosticsSource", "visible error", cause)
                runCurrent()

                val request = server.takeRequest(1, TimeUnit.SECONDS)
                assertThat(request).isNotNull()
                request!!
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.path).isEqualTo("/diagnostics/events")
                assertThat(request.getHeader(Network.Request.Header.USER_AGENT)).isEqualTo("OwnID-Test/1.0")
                assertThat(request.getHeader(Network.Request.Header.ACCEPT_LANGUAGE)).isEqualTo("en-US")
                assertThat(request.getHeader(Network.Request.Header.OWNID_APP_URL)).isEqualTo("app123.server.ownid.com")
                assertThat(request.getHeader(Network.Request.Header.BAGGAGE)).isEqualTo("sdk.correlation_id=correlation-1")

                val body = SerializationImpl().json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val metadata = body.getValue("metadata").jsonObject
                assertThat(body.getValue("component").jsonPrimitive.content).isEqualTo("AndroidSdk")
                assertThat(body.getValue("level").jsonPrimitive.content).isEqualTo("ERROR")
                assertThat(body.getValue("codeInitiator").jsonPrimitive.content).isEqualTo("[diagnostics-test]DiagnosticsSource")
                assertThat(body.getValue("message").jsonPrimitive.content).isEqualTo("DiagnosticsSource => visible error")
                assertThat(body.getValue("exception").jsonPrimitive.content).contains("IllegalStateException: broken diagnostic")
                assertThat(body.getValue("userAgent").jsonPrimitive.content).isEqualTo("OwnID-Test/1.0")
                assertThat(body.getValue("version").jsonPrimitive.content).isEqualTo("42.7")
                assertThat(body).containsKey("sourceTimestamp")
                assertThat(metadata.getValue("correlationId").jsonPrimitive.content).isEqualTo("correlation-1")
                assertThat(metadata.getValue("packageName").jsonPrimitive.content).isEqualTo("com.example.diagnostics")
                assertThat(metadata.getValue("isUserVerifyingPlatformAuthenticatorAvailable").jsonPrimitive.content).isEqualTo("true")
                assertThat(metadata.getValue("isDeviceSecured").jsonPrimitive.content).isEqualTo("true")
                assertThat(metadata.getValue("isFingerprintHardwarePresent").jsonPrimitive.content).isEqualTo("false")
                assertThat(metadata.getValue("isFaceHardwarePresent").jsonPrimitive.content).isEqualTo("true")
                assertThat(metadata.getValue("isIrisHardwarePresent").jsonPrimitive.content).isEqualTo("false")
                assertThat(metadata.getValue("isStrongBiometricEnabled").jsonPrimitive.content).isEqualTo("true")
            } finally {
                instanceJob.cancel()
                runCurrent()
            }
        }
    }

    @Test
    fun serializationFailureLogsLocallyAndSkipsNetwork(): Unit = runTest {
        val network = RecordingServerNetwork()
        val localLogs = mutableListOf<LogEntry>()
        val instanceJob = Job()
        val logger = serverLogger(
            network = network,
            serialization = FailingSerialization(),
            dispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            ownIdLogger = recordingLogger(localLogs)
        )

        try {
            runCurrent()
            logger.log(OwnIdLogger.Level.Error, "DiagnosticsSource", "not serializable", cause = null)
            runCurrent()

            assertThat(network.requests).isEmpty()
            assertThat(localLogs).contains(
                LogEntry(
                    level = OwnIdLogger.Level.Warn,
                    className = "OwnIdServerLogger",
                    message = "Failed to serialize log item",
                    causeMessage = "forced serialization failure"
                )
            )
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun deterministicHttpFailureLogsLocallyWithoutSchedulingRetry(): Unit = runTest {
        val network = RecordingServerNetwork { request ->
            Network.Response.Fail.HttpError(
                url = request.url,
                statusCode = 503,
                headers = emptyMap(),
                body = "diagnostics unavailable"
            )
        }
        val localLogs = mutableListOf<LogEntry>()
        val instanceJob = Job()
        val logger = serverLogger(
            network = network,
            serialization = SerializationImpl(),
            dispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            ownIdLogger = recordingLogger(localLogs)
        )

        try {
            runCurrent()
            logger.log(OwnIdLogger.Level.Error, "DiagnosticsSource", "http failure", cause = null)
            runCurrent()

            assertThat(network.requests).hasSize(1)
            assertThat(localLogs).contains(
                LogEntry(
                    level = OwnIdLogger.Level.Info,
                    className = "OwnIdServerLogger",
                    message = network.responseString,
                    causeMessage = null
                )
            )
            assertThat(localLogs.any { it.message?.startsWith("Retry scheduled") == true }).isFalse()
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun networkFailuresRetryWithinBoundedVirtualDelaysAndStopAfterRetryLimit(): Unit = runTest {
        val requestTimes = mutableListOf<Long>()
        val network = RecordingServerNetwork { request ->
            requestTimes += testScheduler.currentTime
            Network.Response.Fail.NetworkError(
                url = request.url,
                error = IOException("diagnostics transport unavailable")
            )
        }
        val localLogs = mutableListOf<LogEntry>()
        val instanceJob = Job()
        val logger = serverLogger(
            network = network,
            serialization = SerializationImpl(),
            dispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            ownIdLogger = recordingLogger(localLogs)
        )

        try {
            runCurrent()
            logger.log(OwnIdLogger.Level.Error, "DiagnosticsSource", "network failure", cause = null)
            runCurrent()

            assertThat(network.requests).hasSize(1)

            advanceUntilJustBefore(FIRST_RETRY_WINDOW)

            assertThat(network.requests).hasSize(1)

            advanceUntilEndOf(FIRST_RETRY_WINDOW)

            assertThat(network.requests).hasSize(2)
            requestTimes[1].isWithin(FIRST_RETRY_WINDOW)

            val secondRetryWindow = SECOND_RETRY_WINDOW.after(requestTimes[1])
            advanceUntilJustBefore(secondRetryWindow)

            assertThat(network.requests).hasSize(2)

            advanceUntilEndOf(secondRetryWindow)

            assertThat(network.requests).hasSize(3)
            requestTimes[2].isWithin(secondRetryWindow)

            advancePast(THIRD_RETRY_WINDOW.after(requestTimes[2]))

            assertThat(network.requests).hasSize(3)
            assertThat(localLogs.infoMessagesStartingWith("NetworkError(")).hasSize(3)
            val retryMessages = localLogs.retryScheduleMessages()
            assertThat(retryMessages).hasSize(2)
            assertThat(retryMessages[0]).contains("attempt 1")
            assertThat(retryMessages[1]).contains("attempt 2")
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    @Test
    fun thrownNetworkFailuresRetryOnceWithinBoundedVirtualDelayAndThenStop(): Unit = runTest {
        val requestTimes = mutableListOf<Long>()
        val network = RecordingServerNetwork {
            requestTimes += testScheduler.currentTime
            throw IOException("diagnostics execute failed")
        }
        val localLogs = mutableListOf<LogEntry>()
        val instanceJob = Job()
        val logger = serverLogger(
            network = network,
            serialization = SerializationImpl(),
            dispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = instanceJob,
            ownIdLogger = recordingLogger(localLogs)
        )

        try {
            runCurrent()
            logger.log(OwnIdLogger.Level.Error, "DiagnosticsSource", "thrown network failure", cause = null)
            runCurrent()

            assertThat(network.requests).hasSize(1)

            advanceUntilJustBefore(FIRST_RETRY_WINDOW)

            assertThat(network.requests).hasSize(1)

            advanceUntilEndOf(FIRST_RETRY_WINDOW)

            assertThat(network.requests).hasSize(2)
            requestTimes[1].isWithin(FIRST_RETRY_WINDOW)

            advancePast(SECOND_RETRY_WINDOW.after(requestTimes[1]))

            assertThat(network.requests).hasSize(2)
            assertThat(localLogs.infoMessagesEqualTo("Failed to send postponed log: diagnostics execute failed")).hasSize(2)
            val retryMessages = localLogs.retryScheduleMessages()
            assertThat(retryMessages).hasSize(1)
            assertThat(retryMessages.single()).contains("attempt 1")
        } finally {
            instanceJob.cancel()
            runCurrent()
        }
    }

    private fun serverLogger(
        server: MockWebServer,
        appConfigProvider: AppConfigProvider,
        dispatcher: CoroutineDispatcher,
        instanceJob: Job,
        eventListener: EventListener? = null
    ): ServerLogger {
        val localInfo = diagnosticsLocalInfo()
        val okHttpClientBuilder = DefaultOkHttpClientFactory.create(
            localInfo = localInfo,
            languageTagsProvider = languageTagsProvider(LanguageTag("en", "US")),
            appUrlHeaderValue = "app123.server.ownid.com",
            ownIdLogger = null
        ).newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
        if (eventListener != null) okHttpClientBuilder.eventListener(eventListener)
        val okHttpClient = okHttpClientBuilder.build()

        return ServerLogger(
            instanceName = InstanceName("diagnostics-test"),
            configuration = diagnosticsConfiguration(rootUrl = server.url("/diagnostics/").toString()),
            localInfo = localInfo,
            appConfigProvider = appConfigProvider,
            network = NetworkImpl(okHttpClient = okHttpClient, retryConfig = Network.Request.RetryConfig.Default),
            serialization = SerializationImpl(),
            dispatcher = dispatcher,
            instanceJob = instanceJob,
            ownIdLogger = null
        )
    }

    private fun serverLogger(
        network: Network,
        serialization: Serialization,
        dispatcher: CoroutineDispatcher,
        instanceJob: Job,
        ownIdLogger: OwnIdLogger?
    ): ServerLogger =
        ServerLogger(
            instanceName = InstanceName("diagnostics-test"),
            configuration = diagnosticsConfiguration(rootUrl = "https://diagnostics.example/diagnostics/"),
            localInfo = diagnosticsLocalInfo(),
            appConfigProvider = ImmediateAppConfigProvider(diagnosticsAppConfig(AppConfig.LogLevel.DEBUG)),
            network = network,
            serialization = serialization,
            dispatcher = dispatcher,
            instanceJob = instanceJob,
            ownIdLogger = ownIdLogger
        )

    private fun languageTagsProvider(vararg tags: LanguageTag): LanguageTagsProvider = object : LanguageTagsProvider {
        override val languageTagsFlow: StateFlow<List<LanguageTag>> = MutableStateFlow(tags.toList())

        override fun setLanguageTags(tags: List<String>) = Unit
    }

    private fun recordingLogger(entries: MutableList<LogEntry>): OwnIdLogger = object : OwnIdLogger {
        override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
        override val tag: String = "OwnID-ServerLogger-Test"

        override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
            entries += LogEntry(level = level, className = className, message = message, causeMessage = cause?.message)
        }
    }

    private class ControlledAppConfigProvider(initial: AppConfig) : AppConfigProvider {
        private val state = MutableStateFlow(initial)
        private val fetchResult = CompletableDeferred<AppConfig>()

        override val config: Flow<AppConfig> = state

        override suspend fun getOrFetchConfig(): AppConfig = fetchResult.await()

        fun completeFetch(config: AppConfig) {
            state.value = config
            fetchResult.complete(config)
        }

        fun update(config: AppConfig) {
            state.value = config
        }
    }

    private class FailingSerialization : Serialization {
        override val json: Json = SerializationImpl().json

        override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String =
            throw IllegalStateException("forced serialization failure")
    }

    private class CallCompletionListener : EventListener() {
        private val completions = Channel<Unit>(Channel.UNLIMITED)

        override fun callEnd(call: Call) {
            completions.trySend(Unit)
        }

        override fun callFailed(call: Call, ioe: IOException) {
            completions.trySend(Unit)
        }

        suspend fun awaitCompletion() {
            completions.receive()
        }
    }

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String?,
        val causeMessage: String?
    )

    private fun TestScope.advanceUntilJustBefore(window: RetryWindow) {
        advanceTimeBy((window.earliestMs - testScheduler.currentTime - 1).milliseconds)
        runCurrent()
    }

    private fun TestScope.advanceUntilEndOf(window: RetryWindow) {
        advanceTimeBy((window.latestMs - testScheduler.currentTime).milliseconds)
        runCurrent()
    }

    private fun TestScope.advancePast(window: RetryWindow) {
        advanceTimeBy((window.latestMs - testScheduler.currentTime + 1).milliseconds)
        runCurrent()
    }

    private fun Long.isWithin(window: RetryWindow) {
        assertThat(this).isAtLeast(window.earliestMs)
        assertThat(this).isAtMost(window.latestMs)
    }

    private fun List<LogEntry>.infoMessagesStartingWith(prefix: String): List<String?> =
        filter { entry -> entry.level == OwnIdLogger.Level.Info && entry.message?.startsWith(prefix) == true }.map { it.message }

    private fun List<LogEntry>.infoMessagesEqualTo(message: String): List<String?> =
        filter { entry -> entry.level == OwnIdLogger.Level.Info && entry.message == message }.map { it.message }

    private fun List<LogEntry>.retryScheduleMessages(): List<String?> =
        filter { entry -> entry.level == OwnIdLogger.Level.Debug && entry.message?.startsWith("Retry scheduled after ") == true }
            .map { it.message }

    private fun RetryWindow.after(startMs: Long): RetryWindow =
        RetryWindow(startMs + earliestMs, startMs + latestMs)

    private data class RetryWindow(val earliestMs: Long, val latestMs: Long)

    private companion object {
        val FIRST_RETRY_WINDOW = RetryWindow(earliestMs = 500L, latestMs = 625L)
        val SECOND_RETRY_WINDOW = RetryWindow(earliestMs = 1_000L, latestMs = 1_250L)
        val THIRD_RETRY_WINDOW = RetryWindow(earliestMs = 2_000L, latestMs = 2_500L)
    }
}
