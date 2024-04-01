package com.ownid.sdk.internal.events

import android.util.Log
import androidx.annotation.RestrictTo
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdLogger
import com.ownid.sdk.internal.flow.OwnIdFlowType
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toSHA256Bytes
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdInternalEventsService(
    private val configuration: Configuration,
    private val correlationId: String,
    private val okHttpClient: OkHttpClient
) {

    private companion object {
        private val JSON_MEDIA_TYPE: MediaType = "application/json".toMediaType()
        private val CACHE_CONTROL_FORCE_NETWORK_NO_CACHE: CacheControl = CacheControl.Builder().noCache().noStore().build()
        private val service: ExecutorService = ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())
    }

    private val eventsUrl: HttpUrl = configuration.getEventsUrl()
    private var loginId: String? = null
    private var context: String? = null

    @Synchronized
    @JvmSynthetic
    internal fun setFlowLoginId(loginId: String?) {
        this.loginId = loginId?.ifBlank { null }
    }

    @Synchronized
    @JvmSynthetic
    internal fun setFlowContext(context: String?) {
        this.context = context
    }

    @JvmSynthetic
    public fun sendMetric(
        flowType: OwnIdFlowType,
        type: Metric.EventType,
        action: String,
        metadata: Metadata? = null,
        source: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null
    ) {
        runCatching {
            val category = when (flowType) {
                OwnIdFlowType.LOGIN -> Metric.Category.Login
                OwnIdFlowType.REGISTER -> Metric.Category.Registration
            }
            val applicationName = if (configuration.isServerConfigurationSet) configuration.server.displayName else null
            val data = metadata?.copy(applicationName, correlationId) ?: Metadata(applicationName, correlationId)

            sendEvent(
                Metric(
                    configuration.packageName, category, type, action, context, data,
                    loginId?.toByteArray()?.toSHA256Bytes()?.toBase64UrlSafeNoPadding(),
                    source, errorMessage, errorCode, configuration.userAgent, configuration.version
                ).toJsonString()
            )
        }.onFailure {
            OwnIdLogger.log(Log.WARN, this@OwnIdInternalEventsService.toClassTag(), "sendMetric", it)
        }
    }

    @JvmSynthetic
    internal fun sendMetric(
        category: Metric.Category,
        type: Metric.EventType,
        action: String,
        context: String,
        metadata: Metadata? = null,
        source: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null,
        siteUrl: String? = null
    ) {
        runCatching {
            val applicationName = if (configuration.isServerConfigurationSet) configuration.server.displayName else null
            val data = metadata?.copy(applicationName, correlationId) ?: Metadata(applicationName, correlationId)

            sendEvent(
                Metric(
                    configuration.packageName, category, type, action, context, data,
                    loginId?.toByteArray()?.toSHA256Bytes()?.toBase64UrlSafeNoPadding(),
                    source, errorMessage, errorCode, configuration.userAgent, configuration.version, siteUrl
                ).toJsonString()
            )
        }.onFailure {
            OwnIdLogger.log(Log.WARN, this@OwnIdInternalEventsService.toClassTag(), "sendMetric", it)
        }
    }

    @JvmSynthetic
    internal fun sendLog(
        level: LogItem.Level, className: String, message: String, context: String?, metadata: Metadata?, errorMessage: String?
    ) {
        runCatching {

            val applicationName = if (configuration.isServerConfigurationSet) configuration.server.displayName else null
            val mdata = metadata?.copy(applicationName, correlationId) ?: Metadata(applicationName, correlationId)
            sendEvent(
                LogItem(level, context, className, message, configuration.userAgent, configuration.version, mdata, errorMessage)
                    .toJsonString()
            )
        }.onFailure {
            OwnIdLogger.log(Log.WARN, this@OwnIdInternalEventsService.toClassTag(), "sendLog", it)
        }
    }

    private fun sendEvent(event: String) {
        runCatching {
            service.submit {
                val request: Request = Request.Builder()
                    .url(eventsUrl)
                    .header("User-Agent", configuration.userAgent)
                    .post(event.toRequestBody(JSON_MEDIA_TYPE))
                    .cacheControl(CACHE_CONTROL_FORCE_NETWORK_NO_CACHE)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful.not() || HttpURLConnection.HTTP_OK != response.code)
                        OwnIdLogger.log(Log.WARN, this@OwnIdInternalEventsService.toClassTag(), "Fail to send event to server: $response\n$event")
                }
            }
        }.onFailure {
            OwnIdLogger.log(Log.WARN, this@OwnIdInternalEventsService.toClassTag(), "Fail to submit event to server: $event", it)
        }
    }

    private fun Any.toClassTag(): String = "${this.javaClass.simpleName}#${this.hashCode()}@${Thread.currentThread().name}"
}