package com.ownid.sdk.internal.events

import androidx.annotation.GuardedBy
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdLogger
import okhttp3.CacheControl
import okhttp3.ConnectionSpec
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@InternalOwnIdAPI
internal class EventsNetworkService(private val configuration: Configuration) {

    private companion object {
        @JvmStatic
        private val client = OkHttpClient.Builder()
            .followRedirects(false)
            .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        @JvmStatic
        private val DEFAULT_MEDIA_TYPE: MediaType = "application/json".toMediaType()

        @JvmStatic
        private val DEFAULT_CACHE_CONTROL_FORCE_NETWORK_NO_CACHE: CacheControl =
            CacheControl.Builder().noCache().noStore().build()
    }

    private val service: ExecutorService = ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())

    private val serverLogLevelLock = Any()

    @GuardedBy("serverLogLevelLock")
    private var serverLogLevel: LogItem.Level = LogItem.Level.WARN

    init {
        runCatching {
            service.submit {
                val clientConfigUrl = configuration.ownIdUrl.newBuilder()
                    .addEncodedPathSegments("client-config")
                    .build()

                val request: Request = Request.Builder()
                    .url(clientConfigUrl)
                    .header("X-API-Version", Configuration.X_API_VERSION)
                    .header("User-Agent", configuration.userAgent)
                    .get()
                    .cacheControl(DEFAULT_CACHE_CONTROL_FORCE_NETWORK_NO_CACHE)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful.not()) {
                        OwnIdLogger.w("EventsNetworkService", "Fail to get log level: $response")
                        return@submit
                    }

                    val clientConfigJson = response.body!!.string()
                    runCatching {
                        val logLevel = JSONObject(clientConfigJson).getInt("logLevel")
                        LogItem.Level.values().firstOrNull { it.value == logLevel } ?: LogItem.Level.WARN
                    }.onSuccess {
                        OwnIdLogger.d("EventsNetworkService", "Setting log level to: $it")
                        synchronized(serverLogLevelLock) { serverLogLevel = it }
                    }.onFailure {
                        OwnIdLogger.w("EventsNetworkService", "Fail to parse log level: $clientConfigJson => $it")
                    }
                }
            }
        }.onFailure {
            OwnIdLogger.w("EventsNetworkService", "Fail to get log level: $it")
        }
    }

    @JvmSynthetic
    internal fun submitLogRunnable(logItem: LogItem) {
        synchronized(serverLogLevelLock) { if (logItem.thisLevelOrAbove(serverLogLevel).not()) return }

        runCatching {
            service.submit {
                val logJsonString = logItem.toJsonString()
                val request: Request = Request.Builder()
                    .url(configuration.ownIdEventsUrl)
                    .header("X-API-Version", Configuration.X_API_VERSION)
                    .header("User-Agent", configuration.userAgent)
                    .post(logJsonString.toRequestBody(DEFAULT_MEDIA_TYPE))
                    .cacheControl(DEFAULT_CACHE_CONTROL_FORCE_NETWORK_NO_CACHE)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful.not() || HttpURLConnection.HTTP_OK != response.code)
                        OwnIdLogger.w("EventsNetworkService", "Fail to send log to server: $response")
                }
            }
        }.onFailure {
            OwnIdLogger.w("EventsNetworkService", "Fail to submit log to server: ${it.message}")
        }
    }

    @JvmSynthetic
    internal fun submitMetricRunnable(metricItem: MetricItem) {
        runCatching {
            service.submit {
                val metricJsonString = metricItem.toJsonString()
                val request: Request = Request.Builder()
                    .url(configuration.ownIdEventsUrl)
                    .header("X-API-Version", Configuration.X_API_VERSION)
                    .header("User-Agent", configuration.userAgent)
                    .post(metricJsonString.toRequestBody(DEFAULT_MEDIA_TYPE))
                    .cacheControl(DEFAULT_CACHE_CONTROL_FORCE_NETWORK_NO_CACHE)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful.not() || HttpURLConnection.HTTP_OK != response.code)
                        OwnIdLogger.w("EventsNetworkService", "Fail to send metric to server: $response")
                }
            }
        }.onFailure {
            OwnIdLogger.w("EventsNetworkService", "Fail to submit metric to server: ${it.message}")
        }
    }
}