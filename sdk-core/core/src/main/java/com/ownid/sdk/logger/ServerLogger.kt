package com.ownid.sdk.logger

import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.AppConfigProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.model.AppConfig
import com.ownid.sdk.model.InstanceName
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.toStringPrefix
import com.ownid.sdk.toStringSuffix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Instance-scoped server log sink for diagnostics allowed by remote AppConfig.
 *
 * Local SDK logging and server logging are separate sinks. This logger accepts SDK diagnostics for one [InstanceName],
 * waits until remote AppConfig is available, filters events by the server-configured level, and posts accepted events to
 * OwnID diagnostics using the instance configuration.
 *
 * Delivery is best effort. Calls to [log] never suspend or report success to the caller. Diagnostics can be dropped when
 * the instance is shutting down, remote logging is disabled, payload preparation fails, or delivery cannot complete.
 * Transport failures may be retried internally. Local logging may receive summaries of diagnostics delivery failures,
 * but those failures do not affect the SDK operation that emitted the diagnostic.
 *
 * Server diagnostics may include the source label, message, exception text, SDK version, request client string, and device/app
 * metadata available from [LocalInfo]. HTTP logging for diagnostics posts is suppressed so diagnostics do not recursively
 * log themselves.
 */
internal class ServerLogger(
    private val instanceName: InstanceName,
    configuration: OwnIdConfiguration,
    private val localInfo: LocalInfo,
    private val appConfigProvider: AppConfigProvider,
    private val network: Network,
    private val serialization: Serialization,
    dispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val ownIdLogger: OwnIdLogger? = null
) : CoroutineScope by CoroutineScope(dispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.ServerLogger")) {

    private data class PostponedLog(
        val level: OwnIdLogger.Level,
        val className: String,
        val message: String,
        val stackTrace: String?,
        val resendCount: Int = 0
    )

    private val logsBox = Channel<PostponedLog>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val currentServerLevel = appConfigProvider.config
        .map { it.logLevel.toOwnIdLoggerLevel() }
        .stateIn(this, SharingStarted.Eagerly, AppConfig.DEFAULT.logLevel.toOwnIdLoggerLevel())

    private val eventsUrl: HttpUrl = when (val rootUrl = configuration.rootUrl) {
        null -> "https://${configuration.appId}.server${configuration.toStringPrefix()}.ownid${configuration.region.toStringSuffix()}.com/events".toHttpUrl()
        else -> rootUrl.toHttpUrl().newBuilder().addPathSegment("events").build()
    }

    private val metadata = Metadata(
        correlationId = localInfo.correlationId,
        packageName = localInfo.packageName,
        isUserVerifyingPlatformAuthenticatorAvailable = localInfo.isSystemFidoCapable,
        isDeviceSecured = localInfo.isDeviceSecured,
        isFingerprintHardwarePresent = localInfo.isFingerprintHardwarePresent,
        isFaceHardwarePresent = localInfo.isFaceHardwarePresent,
        isIrisHardwarePresent = localInfo.isIrisHardwarePresent,
        isStrongBiometricEnabled = localInfo.isStrongBiometricEnabled
    )

    init {
        launch {
            appConfigProvider.getOrFetchConfig()

            logsBox.consumeAsFlow()
                .catch { ownIdLogger?.log(OwnIdLogger.Level.Warn, "OwnIdServerLogger", "Failed to process log: ${it.message}", cause = it) }
                .collect { log ->
                    if (currentServerLevel.value.isEnabled(log.level)) {
                        sendPostponedLog(log)
                    }
                }

        }.invokeOnCompletion { logsBox.close() }
    }

    /** Records a best-effort server diagnostic unless [level] is [OwnIdLogger.Level.Off]. */
    internal fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
        if (level == OwnIdLogger.Level.Off) return

        val item = PostponedLog(
            level = level,
            className = "[$instanceName]$className",
            message = "$className => $message",
            stackTrace = cause?.stackTraceToString()
        )

        logsBox.trySend(item)
    }

    private suspend fun sendPostponedLog(postponedLog: PostponedLog) {
        try {
            val logItem = LogItem(
                level = when (postponedLog.level) {
                    OwnIdLogger.Level.Verbose -> AppConfig.LogLevel.DEBUG
                    OwnIdLogger.Level.Debug -> AppConfig.LogLevel.DEBUG
                    OwnIdLogger.Level.Info -> AppConfig.LogLevel.INFORMATION
                    OwnIdLogger.Level.Warn -> AppConfig.LogLevel.WARNING
                    OwnIdLogger.Level.Error -> AppConfig.LogLevel.ERROR
                    OwnIdLogger.Level.Assert -> AppConfig.LogLevel.ERROR
                    OwnIdLogger.Level.Off -> return
                },
                codeInitiator = postponedLog.className,
                message = postponedLog.message,
                exception = postponedLog.stackTrace,
                metadata = metadata,
                userAgent = localInfo.userAgent,
                version = localInfo.appVersion
            )

            val bodyString = runCatchingExceptions { serialization.encodeToString(LogItem.serializer(), logItem) }.onFailure {
                ownIdLogger?.log(OwnIdLogger.Level.Warn, "OwnIdServerLogger", "Failed to serialize log item", cause = it)
            }.getOrNull() ?: return

            val request = Network.Request.Builder(url = eventsUrl).apply {
                body = bodyString
                suppressHttpLog()
            }.build()

            when (val response = network.execute(request)) {
                is Network.Response.Success -> Unit
                is Network.Response.Fail -> {
                    ownIdLogger?.log(OwnIdLogger.Level.Info, "OwnIdServerLogger", response.toString())
                    if (response is Network.Response.Fail.NetworkError) {
                        if (postponedLog.resendCount < 2) scheduleRetry(postponedLog)
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ownIdLogger?.log(OwnIdLogger.Level.Info, "OwnIdServerLogger", "Failed to send postponed log: ${error.message}", cause = error)
            if (postponedLog.resendCount < 1) {
                scheduleRetry(postponedLog)
            }
        }
    }

    private fun scheduleRetry(postponedLog: PostponedLog) {
        val nextCount = postponedLog.resendCount + 1
        val delayMs = retryDelayMs(nextCount)
        launch {
            try {
                delay(delayMs.milliseconds)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            logsBox.trySend(postponedLog.copy(resendCount = nextCount))
            ownIdLogger?.log(
                OwnIdLogger.Level.Debug,
                "OwnIdServerLogger",
                "Retry scheduled after ${delayMs}ms (attempt $nextCount)",
                cause = null
            )
        }
    }

    private fun retryDelayMs(attempt: Int): Long {
        val baseMs = 500L
        val maxMs = 2_000L
        val factor = 1L shl kotlin.math.max(0, attempt - 1)
        val delay = (baseMs * factor).coerceAtMost(maxMs)
        val jitter = Random.nextLong(0, delay / 4 + 1)
        return delay + jitter
    }

    private fun AppConfig.LogLevel.toOwnIdLoggerLevel(): OwnIdLogger.Level = when (this) {
        AppConfig.LogLevel.DEBUG -> OwnIdLogger.Level.Debug
        AppConfig.LogLevel.INFORMATION -> OwnIdLogger.Level.Info
        AppConfig.LogLevel.WARNING -> OwnIdLogger.Level.Warn
        AppConfig.LogLevel.ERROR -> OwnIdLogger.Level.Error
        AppConfig.LogLevel.NONE -> OwnIdLogger.Level.Off
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    private class LogItem(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        private val component: String = "AndroidSdk",
        private val level: AppConfig.LogLevel,
        private val codeInitiator: String,
        private val message: String,
        private val exception: String? = null,
        private val metadata: Metadata,
        private val userAgent: String,
        private val version: String,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS)
        private val sourceTimestamp: String = "${System.currentTimeMillis()}"
    )

    @Serializable
    private class Metadata(
        private val correlationId: String,
        private val packageName: String,
        private val isUserVerifyingPlatformAuthenticatorAvailable: Boolean,
        private val isDeviceSecured: Boolean?,
        private val isFingerprintHardwarePresent: Boolean?,
        private val isFaceHardwarePresent: Boolean?,
        private val isIrisHardwarePresent: Boolean?,
        private val isStrongBiometricEnabled: Boolean?
    )
}
