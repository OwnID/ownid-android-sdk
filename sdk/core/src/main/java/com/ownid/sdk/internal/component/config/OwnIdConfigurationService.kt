package com.ownid.sdk.internal.component.config

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCallback
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import com.ownid.sdk.internal.component.events.LogItem
import com.ownid.sdk.internal.component.locale.OwnIdLocaleService
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.LinkedList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Class for fetching server configuration for OwnID application.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdConfigurationService(
    private val configuration: Configuration,
    private val localeService: OwnIdLocaleService,
    context: Context,
    okHttpClient: OkHttpClient,
) {
    private val okHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .cache(Cache(directory = File(context.cacheDir, "ownid_config_cache"), maxSize = 1L * 1024L * 1024L))
        .build()

    private val callbacksQueue: LinkedList<OwnIdCallback<Unit>> = LinkedList()
    private var serverConfigRequestInProgress: Boolean = false

    init {
        OwnIdInternalLogger.logD(this, "init", "Invoked")
    }

    internal companion object {
        @VisibleForTesting
        @Throws(OwnIdException::class, JSONException::class, IllegalArgumentException::class)
        internal fun fromServerResponse(response: String): OwnIdServerConfiguration {
            val jsonResponse = JSONObject(response)

            var redirectURL: String? = null
            if (jsonResponse.has("redirectUrl")) {
                val uri = Uri.parse(jsonResponse.optString("redirectUrl")).normalizeScheme()
                if (uri.isAbsolute) redirectURL = uri.toString()
                else OwnIdInternalLogger.logE(this, "fromServerResponse", "'redirectUrl' must contain an explicit scheme: $uri")
            }

            val locales = jsonResponse.optJSONArray("supportedLocales") ?: JSONArray("en")
            val supportedLocales = if (locales.length() == 0) setOf("en") else List(locales.length()) { locales.getString(it) }.toSet()

            val phoneCodesJson = jsonResponse.optJSONArray("phoneCodes") ?: JSONArray()
            val phoneCodes = List(phoneCodesJson.length()) { i ->
                phoneCodesJson.optJSONObject(i)?.let { OwnIdServerConfiguration.PhoneCode.fromResponse(it) } ?: run {
                    OwnIdInternalLogger.logW(this, "fromServerResponse", "Unexpected phone code data: '${phoneCodesJson.optString(i)}'")
                    null
                }
            }.filterNotNull()

            val serverUrl = jsonResponse.getString("serverUrl").toHttpUrl()
            if (serverUrl.isHttps.not()) {
                throw OwnIdException("fromServerResponse: Only https supported as 'serverUrl': $serverUrl")
            }
            if ("ownid.com".equals(serverUrl.topPrivateDomain(), true).not() && "ownid-eu.com".equals(serverUrl.topPrivateDomain(), true).not()) {
                throw OwnIdException("fromServerResponse: ServerUrl is not ownid.com or ownid-eu.com url: $serverUrl")
            }

            val origin = jsonResponse.optJSONArray("origin")
                ?.let { a -> List(a.length()) { a.optString(it) } }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()

            return OwnIdServerConfiguration(
                LogItem.Level.fromString(jsonResponse.optString("logLevel")),
                redirectURL,
                OwnIdServerConfiguration.AndroidSettings.fromResponse(jsonResponse),
                jsonResponse.optBoolean("passkeysAutofillEnabled"),
                jsonResponse.optBoolean("enableRegistrationFromLogin"),
                supportedLocales,
                OwnIdServerConfiguration.LoginId.fromResponse(jsonResponse),
                jsonResponse.optString("logoUrl").ifBlank { null },
                origin,
                jsonResponse.optString("displayName"),
                phoneCodes,
                serverUrl,
                OwnIdServerConfiguration.WebViewSettings.fromResponse(jsonResponse)
            )
        }
    }

    @MainThread
    @JvmSynthetic
    internal fun ensureConfigurationSet(callback: OwnIdCallback<Unit>) {
        OwnIdInternalLogger.logD(this, "ensureConfigurationSet", "isServerConfigurationSet:${configuration.isServerConfigurationSet}")

        if (configuration.isServerConfigurationSet) {
            callback(Result.success(Unit))
            return
        }

        callbacksQueue.add(callback)

        if (serverConfigRequestInProgress.not()) {
            serverConfigRequestInProgress = true

            doGetRequest(configuration.userAgent, configuration.getServerConfigurationUrl(), Handler(Looper.getMainLooper())) {
                val result = mapCatching { setServerConfiguration(it) }.recoverCatching {
                    if (it is OwnIdException && it.cause is UnknownHostException) {
                        OwnIdInternalLogger.logI(this@OwnIdConfigurationService, "ensureConfigurationSet", it.message)
                    } else {
                        OwnIdInternalLogger.logW(this@OwnIdConfigurationService, "ensureConfigurationSet", it.message, it)
                    }
                    OwnIdInternalLogger.setLogLevel(LogItem.Level.WARNING)
                    throw OwnIdException("No server configuration available", it)
                }

                while (callbacksQueue.isNotEmpty()) callbacksQueue.pollFirst()?.invoke(result)
                serverConfigRequestInProgress = false
            }
        }
    }

    @MainThread
    @JvmSynthetic
    @Throws(OwnIdException::class)
    internal suspend fun ensureConfigurationSet() = suspendCoroutine { continuation ->
        ensureConfigurationSet(object : (Result<Unit>) -> Unit {
            override fun invoke(result: Result<Unit>) {
                result.onSuccess { continuation.resume(it) }
                result.onFailure { continuation.resumeWithException(it) }
            }
        })
    }

    private fun Configuration.getServerConfigurationUrl(): HttpUrl = "https://cdn.${env}ownid${region}.com/sdk/$appId/mobile".toHttpUrl()

    private fun setServerConfiguration(serverConfiguration: OwnIdServerConfiguration) {
        OwnIdInternalLogger.logD(this, "setServerConfiguration", "Invoked")

        configuration.setServerConfiguration(serverConfiguration)
        OwnIdInternalLogger.setLogLevel(serverConfiguration.logLevel)
        localeService.serverSupportedLocalesUpdated()

        configuration.verify()
    }

    private fun doGetRequest(userAgent: String, url: HttpUrl, handler: Handler, callback: OwnIdCallback<OwnIdServerConfiguration>) {
        OwnIdInternalLogger.logD(this, "doGetRequest", "$url")

        val request: Request = Request.Builder().url(url).header("User-Agent", userAgent).get().build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { callback(Result.failure(OwnIdException("Request fail ($url) ${e.message}", e))) }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    if (response.isSuccessful) response.use { it.body!!.string() }
                    else throw OwnIdException("Server response: [${response.code}] => ${response.message}")
                }
                handler.post { callback(result.mapCatching { fromServerResponse(it) }) }
            }
        })
    }
}