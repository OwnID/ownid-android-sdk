package com.ownid.sdk.capability.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.AtomicFile
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.api.ApiBaseUrl
import com.ownid.sdk.api.fold
import com.ownid.sdk.api.impl.ApiCallInterceptor
import com.ownid.sdk.api.impl.config.AppConfigApiImpl
import com.ownid.sdk.api.internal.AppConfigApi
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.DispatcherProvider
import com.ownid.sdk.capability.LoginIdConfigurationProvider
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.env
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LoginIdConfiguration
import com.ownid.sdk.model.runCatchingExceptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Instance-scoped [AppConfigProvider] implementation.
 *
 * Applies the first usable app configuration for the instance: fresh remote configuration when available, otherwise
 * the stored configuration for the same app/environment, otherwise [AppConfig.DEFAULT]. Later successful remote
 * configuration replaces the active value and is stored for future startup fallback.
 *
 * Remote, decoding, and persistence failures are logged and do not prevent a usable configuration from being emitted.
 * Corrupted stored configuration is ignored and removed before falling through to [AppConfig.DEFAULT].
 */
@InternalOwnIdAPI
internal class AppConfigProviderImpl(
    context: Context,
    configuration: OwnIdConfiguration,
    apiBaseUrl: ApiBaseUrl,
    okHttpClient: OkHttpClient,
    private val serialization: Serialization,
    dispatcherProvider: DispatcherProvider,
    job: Job,
    loginIdConfigurationProvider: LoginIdConfigurationProvider?,
    private val logger: OwnIdLogRouter?,
    interceptor: ApiCallInterceptor?
) : AppConfigProvider {

    private companion object {
        private const val APP_CONFIG_TIMEOUT_SECONDS: Long = 5
        private val BOOTSTRAP_TIMEOUT = 2.seconds
        private val RETRY_SCHEDULE_SECONDS = longArrayOf(1, 2, 5, 10, 30, 60, 120, 300)
    }

    private enum class Trigger { STARTUP, RETRY_TIMER, NETWORK_MONITOR }

    private sealed interface AttemptOutcome {
        class Success(val config: AppConfig) : AttemptOutcome
        object Failure : AttemptOutcome
    }

    private sealed interface Event {
        object Start : Event
        object BootstrapTimeout : Event
        object RetryTick : Event
        object NetworkAvailable : Event
        class AttemptCompleted(val trigger: Trigger, val outcome: AttemptOutcome) : Event
    }

    override val config: Flow<AppConfig>
        get() = _configuration.filterNotNull()

    override suspend fun getOrFetchConfig(): AppConfig {
        events.trySend(Event.Start)
        _configuration.value?.let { return it }
        return try {
            _configuration.filterNotNull().first()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _configuration.value ?: AppConfig.DEFAULT
        }
    }

    private val _configuration = MutableStateFlow<AppConfig?>(null)
    private val scope: CoroutineScope = CoroutineScope(dispatcherProvider.ioDispatcher() + SupervisorJob(job) + CoroutineName("OwnId.AppConfigProvider"))
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val configStoreFile = File(
        File(context.noBackupFilesDir, "ownid/config"),
        "appconfig_${configuration.env().name.lowercase()}_${configuration.region.name.lowercase()}_${configuration.appId}.json"
    )

    private val events = Channel<Event>(Channel.BUFFERED)

    private var started = false
    private var bootstrapCompleted = false
    private var inFlightAttempt = false
    private var retryEnabled = false
    private var stopConditionReached = false
    private var retryDelayIndex = 0

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var bootstrapTimerJob: Job? = null
    private var retryTimerJob: Job? = null

    private val appConfigApi: AppConfigApi = AppConfigApiImpl(
        apiBaseUrl = apiBaseUrl,
        network = NetworkImpl(
            okHttpClient = okHttpClient.newBuilder()
                .callTimeout(APP_CONFIG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build(),
            retryConfig = null
        ),
        serialization,
        dispatcherProvider,
        interceptor
    )

    init {
        scope.launch { eventLoop() }
        events.trySend(Event.Start)

        if (loginIdConfigurationProvider != null) {
            config.onEach { c ->
                val normalizedItems = c.loginIdConfig.distinctBy { it.type }
                val supportedTypes = normalizedItems.map { it.type }
                val validationRegexes = normalizedItems.associate { item ->
                    val regex = runCatchingExceptions { item.regex?.toRegex() }.getOrElse { error ->
                        logger?.logW(this@AppConfigProviderImpl, "setConfiguration", "Failed to parse regex for ${item.type}: ${error.message} ", error)
                        null
                    }
                    item.type to regex
                }

                loginIdConfigurationProvider.setServerConfiguration(LoginIdConfiguration(supportedTypes, validationRegexes))
            }.launchIn(scope)
        }

        job.invokeOnCompletion {
            bootstrapTimerJob?.cancel()
            bootstrapTimerJob = null
            retryTimerJob?.cancel()
            retryTimerJob = null
            unregisterNetworkCallback()
            events.close()
        }
    }

    private suspend fun eventLoop() {
        for (event in events) {
            when (event) {
                Event.Start -> {
                    if (!started) {
                        started = true
                        registerNetworkCallback()

                        bootstrapTimerJob?.cancel()
                        bootstrapTimerJob = scope.launch {
                            delay(BOOTSTRAP_TIMEOUT)
                            events.trySend(Event.BootstrapTimeout)
                        }

                        startAttempt(Trigger.STARTUP)
                    }
                }

                Event.BootstrapTimeout -> {
                    if (!bootstrapCompleted) {
                        bootstrapCompleted = true
                        bootstrapTimerJob?.cancel()
                        bootstrapTimerJob = null

                        if (_configuration.value == null) {
                            _configuration.value = loadStoredConfigOrNull() ?: AppConfig.DEFAULT
                        }

                        if (!stopConditionReached) {
                            retryEnabled = true
                        }
                    }
                }

                Event.RetryTick -> {
                    retryTimerJob = null
                    startAttempt(Trigger.RETRY_TIMER)
                }

                Event.NetworkAvailable -> {
                    startAttempt(Trigger.NETWORK_MONITOR)
                }

                is Event.AttemptCompleted -> {
                    inFlightAttempt = false

                    when (val outcome = event.outcome) {
                        is AttemptOutcome.Success -> {
                            _configuration.value = outcome.config
                            persistConfig(outcome.config)

                            if (!stopConditionReached) {
                                stopConditionReached = true
                                bootstrapCompleted = true
                                retryEnabled = false
                                bootstrapTimerJob?.cancel()
                                bootstrapTimerJob = null
                                retryTimerJob?.cancel()
                                retryTimerJob = null
                                unregisterNetworkCallback()
                            }
                        }

                        AttemptOutcome.Failure -> {
                            if (!bootstrapCompleted) {
                                bootstrapCompleted = true
                                bootstrapTimerJob?.cancel()
                                bootstrapTimerJob = null

                                if (_configuration.value == null) {
                                    _configuration.value = loadStoredConfigOrNull() ?: AppConfig.DEFAULT
                                }

                                if (!stopConditionReached) {
                                    retryEnabled = true
                                }
                            } else if (retryEnabled && !stopConditionReached) {
                                logger?.logD(this@AppConfigProviderImpl, "eventLoop", "Retry attempt failed via ${event.trigger.name}")
                            }
                        }
                    }
                }
            }

            if (retryEnabled && !stopConditionReached && !inFlightAttempt && retryTimerJob?.isActive != true) {
                val lastIndex = RETRY_SCHEDULE_SECONDS.lastIndex
                val scheduleIndex = retryDelayIndex.coerceAtMost(lastIndex)
                val delaySeconds = RETRY_SCHEDULE_SECONDS[scheduleIndex]
                if (retryDelayIndex < lastIndex) {
                    retryDelayIndex += 1
                }

                retryTimerJob = scope.launch {
                    delay(delaySeconds.seconds)
                    events.trySend(Event.RetryTick)
                }
            }
        }
    }

    private fun startAttempt(trigger: Trigger): Boolean {
        if (stopConditionReached || inFlightAttempt) return false
        if (trigger != Trigger.STARTUP && !retryEnabled) return false

        inFlightAttempt = true
        scope.launch {
            val outcome = performAttempt(trigger)
            events.trySend(Event.AttemptCompleted(trigger = trigger, outcome = outcome))
        }
        return true
    }

    private suspend fun performAttempt(trigger: Trigger): AttemptOutcome {
        val response = try {
            appConfigApi.start()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger?.logW(this@AppConfigProviderImpl, "performAttempt", "AppConfig attempt failed [${trigger.name}]: ${error.message}", error)
            return AttemptOutcome.Failure
        }

        return response.fold(
            onSuccess = { appConfig ->
                logger?.logD(this@AppConfigProviderImpl, "performAttempt", "Server log level is: ${appConfig.logLevel.name}")
                AttemptOutcome.Success(appConfig)
            },
            onError = { error ->
                logger?.logW(this@AppConfigProviderImpl, "performAttempt", "AppConfig attempt failed [${trigger.name}]: $error")
                AttemptOutcome.Failure
            }
        )
    }

    private fun loadStoredConfigOrNull(): AppConfig? {
        if (!configStoreFile.exists()) return null

        return runCatchingExceptions {
            val payload = configStoreFile.readText()
            serialization.decodeFromString(AppConfig.serializer(), payload)
        }.onFailure { error ->
            logger?.logW(this@AppConfigProviderImpl, "loadStoredConfigOrNull", "Failed to decode stored app config. Deleting corrupted file.", error)
            runCatchingExceptions { configStoreFile.delete() }
        }.getOrNull()
    }

    private fun persistConfig(config: AppConfig) {
        val payload = runCatchingExceptions {
            serialization.encodeToString(AppConfig.serializer(), config)
        }.getOrElse { error ->
            logger?.logW(this@AppConfigProviderImpl, "persistConfig", "Failed to encode app config for persistence", error)
            return
        }

        val parent = configStoreFile.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs() && !parent.exists())) {
            logger?.logW(this@AppConfigProviderImpl, "persistConfig", "Failed to create app config directory: ${parent?.absolutePath}")
            return
        }

        val atomicFile = AtomicFile(configStoreFile)
        var outputStream: FileOutputStream? = null
        try {
            outputStream = atomicFile.startWrite()
            outputStream.write(payload.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(outputStream)
        } catch (error: Exception) {
            outputStream?.let(atomicFile::failWrite)
            logger?.logW(this@AppConfigProviderImpl, "persistConfig", "Failed to persist app config", error)
        }
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && isValidated(caps)) events.trySend(Event.NetworkAvailable)
            }

            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
                if (isValidated(networkCapabilities)) events.trySend(Event.NetworkAvailable)
            }
        }

        runCatchingExceptions {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)
            }
        }.onSuccess {
            networkCallback = callback
        }.onFailure { error ->
            logger?.logI(this, "registerNetworkCallback", "Failed to register app-config network callback", error)
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        runCatchingExceptions { connectivityManager?.unregisterNetworkCallback(cb) }
            .onFailure { error -> logger?.logI(this, "unregisterNetworkCallback", "Failed to unregister app-config network callback", error) }
        networkCallback = null
    }

    private fun isValidated(caps: NetworkCapabilities): Boolean =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
