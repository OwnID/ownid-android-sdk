package com.ownid.sdk.capability.impl

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.OwnIdConfigurationImpl
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: CACHE-RUNTIME-010, CACHE-RUNTIME-020, CACHE-RUNTIME-030, CACHE-RUNTIME-040, CACHE-RUNTIME-060
internal class AppConfigProviderImplRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val serialization = SerializationImpl()

    @Test
    fun startupSuccessEmitsFreshConfigPersistsItUpdatesLoginIdConfigurationAndStopsMonitoring(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(appConfigJson(loginIdType = "PhoneNumber", regex = "^\\+?[1-9]\\d{1,14}$")))
            val loginIdConfigurationProvider = LoginIdConfigurationProviderImpl()
            val handle = provider(server, loginIdConfigurationProvider = loginIdConfigurationProvider)
            try {
                val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val connectivityShadow = shadowOf(connectivity)
                runCurrent()

                assertThat(connectivityShadow.networkCallbacks).hasSize(1)

                val freshConfig = async { handle.provider.config.first { it.loginIdConfig.any { item -> item.type == LoginId.Type.PhoneNumber } } }

                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
                runCurrent()
                val config = freshConfig.await()
                assertThat(config.loginIdConfig).containsExactly(AppConfig.LoginIdConfig(LoginId.Type.PhoneNumber, "^\\+?[1-9]\\d{1,14}$"))
                assertThat(handle.provider.getOrFetchConfig()).isEqualTo(config)
                assertThat(loginIdConfigurationProvider.configuration.supportedTypes).containsExactly(LoginId.Type.PhoneNumber)
                assertThat(loginIdConfigurationProvider.configuration.validationRegexes[LoginId.Type.PhoneNumber]?.pattern).isEqualTo("^\\+?[1-9]\\d{1,14}$")

                val persistedConfig = serialization.decodeFromString(AppConfig.serializer(), handle.configFile.readText())
                assertThat(persistedConfig).isEqualTo(config)

                assertThat(connectivityShadow.networkCallbacks).isEmpty()
                advanceTimeBy(300_000.milliseconds)
                runCurrent()
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun bootstrapTimeoutEmitsStoredConfigWhileStartupAttemptIsStillPending(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val storedConfig = appConfig()
            val handle = provider(server)
            try {
                handle.writeStoredConfig(storedConfig)
                val result = async { handle.provider.getOrFetchConfig() }

                runCurrent()
                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
                advanceTimeBy(2_000.milliseconds)
                runCurrent()

                assertThat(result.await()).isEqualTo(storedConfig)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun concurrentBootstrapGetOrFetchCallsShareSingleInFlightRemoteAttempt(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val storedConfig = appConfig()
            val handle = provider(server)
            try {
                handle.writeStoredConfig(storedConfig)
                val first = async { handle.provider.getOrFetchConfig() }
                val second = async { handle.provider.getOrFetchConfig() }

                runCurrent()

                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
                assertThat(server.requestCount).isEqualTo(1)

                advanceTimeBy(2_000.milliseconds)
                runCurrent()

                assertThat(first.await()).isEqualTo(storedConfig)
                assertThat(second.await()).isEqualTo(storedConfig)
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun bootstrapTimeoutDeletesCorruptedStoredConfigAndEmitsDefaultConfig(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val handle = provider(server)
            try {
                handle.configFile.parentFile!!.mkdirs()
                handle.configFile.writeText("{not-json")
                val result = async { handle.provider.getOrFetchConfig() }

                runCurrent()
                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
                advanceTimeBy(2_000.milliseconds)
                runCurrent()

                assertThat(result.await()).isEqualTo(AppConfig.DEFAULT)
                assertThat(handle.configFile.exists()).isFalse()
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun networkFailureRetriesOnVirtualScheduleAndSuccessUnregistersCallback(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"unavailable"}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody(appConfigJson(loginIdType = "UserName")))
            val callCompletionProbe = CallCompletionProbe()
            val handle = provider(server, okHttpClient = okHttpClient(callCompletionProbe))
            try {
                assertFailureFallbackThenRetrySuccess(server, handle, callCompletionProbe) {
                    advanceTimeBy(APP_CONFIG_FIRST_RETRY_DELAY_MS.milliseconds)
                    runCurrent()
                }
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun connectivityCallbackTriggersRetryAfterFailureAndSuccessUnregistersCallback(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"message":"unavailable"}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody(appConfigJson(loginIdType = "UserName")))
            val callCompletionProbe = CallCompletionProbe()
            val handle = provider(server, okHttpClient = okHttpClient(callCompletionProbe))
            try {
                assertFailureFallbackThenRetrySuccess(server, handle, callCompletionProbe) { callback ->
                    triggerValidatedConnectivity(callback)
                }
                advanceTimeBy(APP_CONFIG_FIRST_RETRY_DELAY_MS.milliseconds)
                runCurrent()
                assertThat(server.requestCount).isEqualTo(2)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun connectivityCallbackBeforeRetryIsEnabledDoesNotIssueConfigRequest(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val handle = provider(server)
            try {
                runCurrent()
                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")

                triggerValidatedConnectivity(registeredConnectivityCallbacks().single())

                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun serverDecodeFailureRetriesOnVirtualScheduleAndSuccessUnregistersCallback(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"loginIdConfig": ["""))
            server.enqueue(MockResponse().setResponseCode(200).setBody(appConfigJson(loginIdType = "UserName")))
            val callCompletionProbe = CallCompletionProbe()
            val handle = provider(server, okHttpClient = okHttpClient(callCompletionProbe))
            try {
                assertFailureFallbackThenRetrySuccess(server, handle, callCompletionProbe) {
                    advanceTimeBy(APP_CONFIG_FIRST_RETRY_DELAY_MS.milliseconds)
                    runCurrent()
                }
            } finally {
                handle.close()
            }
        }
    }

    @Test
    fun shutdownUnregistersConnectivityCallback(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val handle = provider(server)
            val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val connectivityShadow = shadowOf(connectivity)

            runCurrent()
            assertThat(connectivityShadow.networkCallbacks).hasSize(1)

            handle.close()

            assertThat(connectivityShadow.networkCallbacks).isEmpty()
        }
    }

    @Test
    fun cacheWriteFailureDoesNotBlockFreshInMemoryConfig(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(appConfigJson(loginIdType = "PhoneNumber")))
            val noBackupDir = tempDirectory()
            val configDirectoryPath = File(noBackupDir, "ownid/config")
            configDirectoryPath.parentFile!!.mkdirs()
            configDirectoryPath.writeText("not-a-directory")
            val handle = provider(server, context = NoBackupFilesContext(app, noBackupDir), noBackupDir = noBackupDir)
            try {
                val freshConfig = async { handle.provider.config.first { it.loginIdConfig.any { item -> item.type == LoginId.Type.PhoneNumber } } }

                runCurrent()
                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
                runCurrent()
                assertThat(freshConfig.await().loginIdConfig).containsExactly(AppConfig.LoginIdConfig(LoginId.Type.PhoneNumber))
                assertThat(handle.provider.getOrFetchConfig().loginIdConfig).containsExactly(AppConfig.LoginIdConfig(LoginId.Type.PhoneNumber))
                assertThat(configDirectoryPath.isFile).isTrue()
                assertThat(handle.configFile.exists()).isFalse()
            } finally {
                handle.close()
            }
        }
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [23, 24, 26])
    fun availabilityWaitsForCapabilitiesAndRepeatedValidatedCallbacksDoNotRetry(): Unit = runTest {
        withFailingProvider { server, handle, probe, callback ->
            val network = ShadowNetwork.newInstance(NETWORK_ID)
            val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            shadowOf(connectivity).setNetworkCapabilities(network, validatedNetworkCapabilities())
            callback.onAvailable(network)
            runCurrent()
            assertThat(probe.startedCalls.get()).isEqualTo(1)

            callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)
            repeat(100) {
                callback.onAvailable(network)
                callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
            }
            runCurrent()
            assertThat(probe.startedCalls.get()).isEqualTo(2)
            assertThat(handle.provider.getOrFetchConfig()).isEqualTo(AppConfig.DEFAULT)
        }
    }

    @Test
    fun initialValidatedCallbackIsNotReplayedAfterStartupFailure(): Unit = runTest {
        MockWebServer().use { server ->
            val releaseResponse = java.util.concurrent.CountDownLatch(1)
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    check(releaseResponse.await(5, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(503)
                }
            }
            val probe = CallCompletionProbe()
            val handle = provider(server, okHttpClient = okHttpClient(probe))
            try {
                runCurrent()
                assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
                val callback = registeredConnectivityCallbacks().single()
                val network = ShadowNetwork.newInstance(NETWORK_ID)
                callback.onAvailable(network)
                callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
                runCurrent()
                assertThat(probe.startedCalls.get()).isEqualTo(1)
                releaseResponse.countDown()
                probe.awaitCompletion()
                runCurrent()
                callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
                runCurrent()
                assertThat(probe.startedCalls.get()).isEqualTo(1)
                advanceTimeBy(APP_CONFIG_FIRST_RETRY_DELAY_MS.milliseconds)
                awaitFailedAttempt(server, probe)
                assertThat(probe.startedCalls.get()).isEqualTo(2)
            } finally {
                releaseResponse.countDown()
                handle.close()
            }
        }
    }

    @Test
    fun validationLossAndNetworkLossEachRearmConnectivityRetry(): Unit = runTest {
        withFailingProvider { server, _, probe, callback ->
            val network = ShadowNetwork.newInstance(NETWORK_ID)
            callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)
            callback.onCapabilitiesChanged(network, ShadowNetworkCapabilities.newInstance())
            runCurrent()
            assertThat(probe.startedCalls.get()).isEqualTo(2)
            callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)
            callback.onLost(network)
            callback.onAvailable(network)
            runCurrent()
            assertThat(probe.startedCalls.get()).isEqualTo(3)
            callback.onCapabilitiesChanged(network, validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)
            assertThat(probe.startedCalls.get()).isEqualTo(4)
        }
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [23, 24, 26])
    fun defaultNetworkSwitchRearmsPreviousNetworkWhileApi23RetainsOtherValidatedNetworks(): Unit = runTest {
        withFailingProvider { server, _, probe, callback ->
            val first = ShadowNetwork.newInstance(NETWORK_ID)
            val second = ShadowNetwork.newInstance(NETWORK_ID + 1)
            callback.onAvailable(first)
            callback.onCapabilitiesChanged(first, validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)
            callback.onAvailable(second)
            callback.onCapabilitiesChanged(second, validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)
            // Default callbacks need not report loss for a replaced default network.
            callback.onAvailable(first)
            callback.onCapabilitiesChanged(first, validatedNetworkCapabilities())
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                awaitFailedAttempt(server, probe)
                assertThat(probe.startedCalls.get()).isEqualTo(4)
            } else {
                runCurrent()
                assertThat(probe.startedCalls.get()).isEqualTo(3)
            }
        }
    }

    @Test
    fun acceleratedFailureReplacesOldTimerAndStaleQueuedTickCannotConsumeNewTimer(): Unit = runTest {
        withFailingProvider { server, handle, probe, callback ->
            val staleTick = readProviderField(handle.provider, "pendingRetryTick")!!
            advanceTimeBy(500.milliseconds)
            callback.onCapabilitiesChanged(ShadowNetwork.newInstance(NETWORK_ID), validatedNetworkCapabilities())
            awaitFailedAttempt(server, probe)

            // Reproduce a delayed sender's already-queued old tick after a replacement timer exists.
            @Suppress("UNCHECKED_CAST")
            val events = readProviderField(handle.provider, "events") as Channel<Any>
            assertThat(events.trySend(staleTick).isSuccess).isTrue()
            runCurrent()
            assertThat(probe.startedCalls.get()).isEqualTo(2)
            advanceTimeBy(1_999.milliseconds)
            runCurrent()
            assertThat(probe.startedCalls.get()).isEqualTo(2)
            advanceTimeBy(1.milliseconds)
            awaitFailedAttempt(server, probe)
            assertThat(probe.startedCalls.get()).isEqualTo(3)
        }
    }

    @Test
    fun retryScheduleAdvancesThroughItsCapWithoutConnectivityHints(): Unit = runTest {
        withFailingProvider { server, _, probe, _ ->
            longArrayOf(1, 2, 5, 10, 30, 60, 120, 300, 300).forEachIndexed { index, seconds ->
                advanceTimeBy((seconds * 1_000 - 1).milliseconds)
                runCurrent()
                assertThat(probe.startedCalls.get()).isEqualTo(index + 1)
                advanceTimeBy(1.milliseconds)
                awaitFailedAttempt(server, probe)
                assertThat(probe.startedCalls.get()).isEqualTo(index + 2)
            }
        }
    }

    private suspend fun TestScope.withFailingProvider(
        block: suspend TestScope.(MockWebServer, ProviderHandle, CallCompletionProbe, ConnectivityManager.NetworkCallback) -> Unit
    ) {
        MockWebServer().use { server ->
            repeat(16) { server.enqueue(MockResponse().setResponseCode(503)) }
            val probe = CallCompletionProbe()
            val handle = provider(server, okHttpClient = okHttpClient(probe))
            try {
                awaitFailedAttempt(server, probe)
                assertThat(handle.provider.getOrFetchConfig()).isEqualTo(AppConfig.DEFAULT)
                block(server, handle, probe, registeredConnectivityCallbacks().single())
            } finally {
                handle.close()
            }
        }
    }

    private suspend fun TestScope.awaitFailedAttempt(server: MockWebServer, probe: CallCompletionProbe) {
        runCurrent()
        assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
        probe.awaitCompletion()
        runCurrent()
    }

    private fun readProviderField(provider: AppConfigProviderImpl, name: String): Any? =
        AppConfigProviderImpl::class.java.getDeclaredField(name).apply { isAccessible = true }.get(provider)

    private fun TestScope.provider(
        server: MockWebServer,
        context: Context = app,
        noBackupDir: File = app.noBackupFilesDir,
        loginIdConfigurationProvider: LoginIdConfigurationProviderImpl? = null,
        okHttpClient: OkHttpClient = okHttpClient()
    ): ProviderHandle {
        val appId = "app${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val configuration = OwnIdConfigurationImpl(
            appId = appId,
            env = OwnIdConfiguration.Env.Prod,
            region = OwnIdConfiguration.Region.US
        )
        val ownerJob = SupervisorJob()
        val provider = AppConfigProviderImpl(
            context = context,
            configuration = configuration,
            apiBaseUrl = TestApiBaseUrl(server.url("/api/")),
            okHttpClient = okHttpClient,
            serialization = serialization,
            dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
            job = ownerJob,
            loginIdConfigurationProvider = loginIdConfigurationProvider,
            logger = null,
            interceptor = null
        )
        val configFile = File(File(noBackupDir, "ownid/config"), "appconfig_prod_us_$appId.json")
        return ProviderHandle(provider, ownerJob, configFile, noBackupDir.takeIf { it != app.noBackupFilesDir })
    }

    private class ProviderHandle(
        val provider: AppConfigProviderImpl,
        private val ownerJob: Job,
        val configFile: File,
        private val ownedNoBackupDir: File?
    ) {
        fun writeStoredConfig(config: AppConfig) {
            configFile.parentFile!!.mkdirs()
            configFile.writeText(SerializationImpl().encodeToString(AppConfig.serializer(), config))
        }

        suspend fun close() {
            ownerJob.cancelAndJoin()
            ownedNoBackupDir?.deleteRecursively()
        }
    }

    private class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
        override fun defaultDispatcher(): CoroutineDispatcher = dispatcher
        override fun ioDispatcher(): CoroutineDispatcher = dispatcher
    }

    private class TestApiBaseUrl(private val baseUrl: HttpUrl) : ApiBaseUrl {
        override fun getBaseUrlOrThrow(): HttpUrl = baseUrl
    }

    private class NoBackupFilesContext(base: Context, private val testNoBackupDir: File) : ContextWrapper(base) {
        override fun getNoBackupFilesDir(): File = testNoBackupDir
    }

    private suspend fun TestScope.assertFailureFallbackThenRetrySuccess(
        server: MockWebServer,
        handle: ProviderHandle,
        callCompletionProbe: CallCompletionProbe,
        triggerRetry: suspend TestScope.(ConnectivityManager.NetworkCallback) -> Unit
    ) {
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val connectivityShadow = shadowOf(connectivity)
        val fallback = async { handle.provider.getOrFetchConfig() }

        runCurrent()
        assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
        callCompletionProbe.awaitCompletion()
        val callback = connectivityShadow.networkCallbacks.single()

        runCurrent()
        assertThat(fallback.await()).isEqualTo(AppConfig.DEFAULT)
        assertThat(connectivityShadow.networkCallbacks).containsExactly(callback)

        val freshConfig = async {
            handle.provider.config.first { config -> config.loginIdConfig.any { item -> item.type == LoginId.Type.UserName } }
        }
        triggerRetry(callback)

        assertThat(server.takeConfigRequestPath()).isEqualTo("/api/config/app")
        callCompletionProbe.awaitCompletion()
        runCurrent()
        assertThat(freshConfig.await().loginIdConfig).containsExactly(AppConfig.LoginIdConfig(LoginId.Type.UserName))
        assertThat(connectivityShadow.networkCallbacks).isEmpty()
        assertThat(server.requestCount).isEqualTo(2)
    }

    private fun registeredConnectivityCallbacks(): List<ConnectivityManager.NetworkCallback> {
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return shadowOf(connectivity).networkCallbacks.toList()
    }

    private fun TestScope.triggerValidatedConnectivity(callback: ConnectivityManager.NetworkCallback) {
        callback.onCapabilitiesChanged(ShadowNetwork.newInstance(NETWORK_ID), validatedNetworkCapabilities())
        runCurrent()
    }

    private fun okHttpClient(callCompletionProbe: CallCompletionProbe? = null): OkHttpClient =
        OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
            .eventListener(object : okhttp3.EventListener() {
                override fun callStart(call: okhttp3.Call) {
                    callCompletionProbe?.startedCalls?.incrementAndGet()
                }
            })
            .build()
            .also { client ->
                if (callCompletionProbe != null) {
                    client.dispatcher.idleCallback = Runnable { callCompletionProbe.recordCompletion() }
                }
            }

    private class CallCompletionProbe {
        val startedCalls = java.util.concurrent.atomic.AtomicInteger()
        private val completions = java.util.concurrent.LinkedBlockingQueue<Unit>()

        fun recordCompletion() {
            completions.offer(Unit)
        }

        fun awaitCompletion() {
            // Wait for the real OkHttp thread without letting runTest advance the virtual retry clock.
            assertThat(completions.poll(10, TimeUnit.SECONDS)).isNotNull()
        }
    }

    private fun appConfig(): AppConfig =
        AppConfig(loginIdConfig = listOf(AppConfig.LoginIdConfig(LoginId.Type.UserName)), logLevel = AppConfig.LogLevel.DEBUG)

    private fun appConfigJson(loginIdType: String, regex: String? = null): String {
        val regexProperty = regex?.let { """, "regex": "${it.replace("\\", "\\\\")}"""" }.orEmpty()
        return """
            {
              "loginIdConfig": [
                {
                  "type": "$loginIdType"$regexProperty
                }
              ],
              "logLevel": "Debug"
            }
        """.trimIndent()
    }

    private fun validatedNetworkCapabilities(): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadowCapabilities = shadowOf(capabilities)
        shadowCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return capabilities
    }

    private fun tempDirectory(): File =
        File(app.cacheDir, "app-config-provider-${UUID.randomUUID()}").apply { mkdirs() }

    private fun MockWebServer.takeConfigRequestPath(): String? =
        takeRequest(1, TimeUnit.SECONDS)?.path

    private companion object {
        const val APP_CONFIG_FIRST_RETRY_DELAY_MS = 1_000L
        const val NETWORK_ID = 24
    }
}
