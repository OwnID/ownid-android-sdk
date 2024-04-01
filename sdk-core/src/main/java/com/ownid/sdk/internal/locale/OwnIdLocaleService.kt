package com.ownid.sdk.internal.locale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.OwnIdInternalLogger
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.io.FileSystem
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Class for performing locale requests to OwnID server.
 */
@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class OwnIdLocaleService(context: Context, private val configuration: Configuration, okHttpClient: OkHttpClient) {

    internal interface LocaleUpdateListener {
        @MainThread
        fun onLocaleUpdated()
    }

    internal var currentOwnIdLocale: OwnIdLocale = OwnIdLocale.DEFAULT
        private set

    internal var unspecifiedErrorUserMessage: String = ""
        private set

    private val updateListenerSet = mutableSetOf<LocaleUpdateListener>()

    private var languageTags: String? = null
    private var languageTagsProvider: (() -> String)? = null
    private var updateCurrentOwnIdLocale: Boolean = true

    @MainThread
    @JvmSynthetic
    internal fun registerLocaleUpdateListener(listener: LocaleUpdateListener) {
        updateListenerSet.add(listener)
    }

    @MainThread
    @JvmSynthetic
    internal fun unregisterLocaleUpdateListener(listener: LocaleUpdateListener) {
        updateListenerSet.remove(listener)
    }

    @MainThread
    @JvmSynthetic
    internal fun setLanguageTags(tags: String?) {
        languageTags = tags
        updateCurrentOwnIdLocale = true
    }

    @MainThread
    @JvmSynthetic
    internal fun setLanguageTagsProvider(provider: (() -> String)?) {
        languageTagsProvider = provider
        updateCurrentOwnIdLocale = true
    }

    @MainThread
    @JvmSynthetic
    internal fun updateCurrentOwnIdLocale(context: Context) {
        if (updateCurrentOwnIdLocale.not()) return
        currentOwnIdLocale = ownIdServerLocales.selectLocale(getLanguageTags(context))
        updateCurrentOwnIdLocale = false
        unspecifiedErrorUserMessage = getString(context, OwnIdLocaleKey.UNSPECIFIED_ERROR)
        OwnIdInternalLogger.logD(this, "updateCurrentOwnIdLocale", "Selected locale: $currentOwnIdLocale")
    }

    @MainThread
    @JvmSynthetic
    internal fun serverSupportedLocalesUpdated() {
        ownIdServerLocales = OwnIdServerLocales(configuration.server.supportedLocales.toList()).apply { saveToCache(localeCache) }
        OwnIdInternalLogger.logD(this, "serverSupportedLocalesUpdated", "Set ${ownIdServerLocales.size()} server locales.")
        updateCurrentOwnIdLocale = true
        updateListenerSet.forEach { listener -> listener.onLocaleUpdated() }
    }

    @MainThread
    @JvmSynthetic
    internal fun getString(context: Context, ownIdLocaleKey: OwnIdLocaleKey): String {
        updateCurrentOwnIdLocale(context)

        if (ownIdServerLocales.containsLocale(currentOwnIdLocale).not() && ownIdServerLocales.containsLocale(OwnIdLocale.DEFAULT).not())
            return context.getString(ownIdLocaleKey.fallbackId)

        val selectedLocaleData = OwnIdLocaleContent.fromCache(currentOwnIdLocale, localeCache)
        if (selectedLocaleData == null || selectedLocaleData.isExpired()) updateLocale(currentOwnIdLocale)

        val defaultLocaleData = OwnIdLocaleContent.fromCache(OwnIdLocale.DEFAULT, localeCache)
        if (defaultLocaleData == null || defaultLocaleData.isExpired()) updateLocale(OwnIdLocale.DEFAULT)

        return getStringForLocale(selectedLocaleData, ownIdLocaleKey) ?: run {
            OwnIdInternalLogger.logI(this, "getString", "Fallback to default locale from '${selectedLocaleData?.ownIdLocale?.serverLanguageTag}' for '$ownIdLocaleKey'")
            getStringForLocale(defaultLocaleData, ownIdLocaleKey)
        } ?: run {
            OwnIdInternalLogger.logI(this, "getString", "Fallback to local value from '${defaultLocaleData?.ownIdLocale?.serverLanguageTag}' for '$ownIdLocaleKey'")
            context.getString(ownIdLocaleKey.fallbackId)
        }
    }

    private val localeCache = DiskLruCache(
        fileSystem = FileSystem.SYSTEM,
        directory = File(context.cacheDir, "ownid_locales"),
        appVersion = 1,
        valueCount = 1,
        maxSize = 5L * 1024L * 1024L,
        taskRunner = TaskRunner.INSTANCE
    ).apply {
        initialize()
    }

    private var ownIdServerLocales = OwnIdServerLocales.fromCache(localeCache)

    private val okHttpClient = okHttpClient.newBuilder()
        .cache(Cache(directory = File(context.cacheDir, "ownid_locales_cache"), maxSize = 5L * 1024L * 1024L))
        .build()
    private val requestsInProgress = Collections.synchronizedSet<String>(mutableSetOf())
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        val languageTags = ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()
        OwnIdInternalLogger.logI(this, "init", "Instance created. App language tags: $languageTags")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                OwnIdInternalLogger.logI(this@OwnIdLocaleService, "onReceive", "Locale change detected: $intent")
                updateCurrentOwnIdLocale = true
                updateCurrentOwnIdLocale(context)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @MainThread
    private fun getLanguageTags(context: Context): String {
        val tags = languageTagsProvider?.let { provider ->
            runCatching { provider.invoke() }.getOrElse {
                OwnIdInternalLogger.logW(this, "getLanguageTags", "languageTagsProvider error: ${it.message}", it)
                ""
            }.ifBlank { null }
        }
            ?: languageTags?.ifBlank { null }
            ?: ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()
        OwnIdInternalLogger.logD(this, "getLanguageTags", "Language tags: $tags")
        return tags
    }

    @MainThread
    private fun getStringForLocale(localeData: OwnIdLocaleContent?, ownIdLocaleKey: OwnIdLocaleKey): String? = when {
        localeData == null -> null

        localeData.hasString(ownIdLocaleKey).not() -> {
            val message = "No translation found for key '$ownIdLocaleKey' in locale '${localeData.ownIdLocale.serverLanguageTag}'"
            OwnIdInternalLogger.logW(this, "getStringForLocale", message)
            null
        }

        else -> runCatching { localeData.getString(ownIdLocaleKey) }.getOrElse {
            val message = "Error for key '$ownIdLocaleKey' in locale '${localeData.ownIdLocale.serverLanguageTag}'"
            OwnIdInternalLogger.logW(this, "getStringForLocale", message, it)
            null
        }
    }

    @MainThread
    private fun updateLocale(ownIdLocale: OwnIdLocale) {
        val url = configuration.getLocaleUrl(ownIdLocale.serverLanguageTag)

        if (requestsInProgress.contains(url.toString())) return
        requestsInProgress.add(url.toString())

        val request: Request = Request.Builder().url(url).header("User-Agent", configuration.userAgent).get().build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val message = "Request fail [$ownIdLocale] ($url) ${e.message}"
                OwnIdInternalLogger.logE(this@OwnIdLocaleService, "updateLocale.onFailure", message, e)
                requestsInProgress.remove(url.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = runCatching {
                    if (response.isSuccessful) response.use { it.body!!.string() }
                    else throw OwnIdException("Server response ($url): ${response.code} ${response.message}")
                }.getOrElse {
                    OwnIdInternalLogger.logE(this@OwnIdLocaleService, "updateLocale.onResponse", "${response.code} $url", it)
                    requestsInProgress.remove(url.toString())
                    return
                }

                mainHandler.post {
                    runCatching {
                        OwnIdLocaleContent(ownIdLocale, JSONObject(jsonString)).apply { saveToCache(localeCache) }
                        OwnIdInternalLogger.logD(this@OwnIdLocaleService, "updateLocale.onResponse", "OK $ownIdLocale")
                        updateListenerSet.forEach { listener -> listener.onLocaleUpdated() }
                    }.onFailure {
                        OwnIdInternalLogger.logE(this@OwnIdLocaleService, "updateLocale.onResponse", "${it.message} $ownIdLocale $url", it)
                    }
                    requestsInProgress.remove(url.toString())
                }
            }
        })
    }
}