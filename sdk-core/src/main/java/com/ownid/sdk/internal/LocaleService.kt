package com.ownid.sdk.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.ownid.sdk.Configuration
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.OwnIdCore
import com.ownid.sdk.R
import com.ownid.sdk.exception.OwnIdException
import com.ownid.sdk.internal.utils.DiskLruCache
import com.ownid.sdk.logD
import com.ownid.sdk.logE
import com.ownid.sdk.logV
import com.ownid.sdk.logW
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.io.FileSystem
import okio.buffer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Class for performing locale requests to OwnID server.
 */
@androidx.annotation.OptIn(InternalOwnIdAPI::class)
public class LocaleService private constructor(
    private val client: OkHttpClient,
    private val ownIdCore: OwnIdCore,
    private val handler: Handler?
) {

    @InternalOwnIdAPI
    internal object Key {
        internal const val SKIP_PASSWORD = "skipPassword"
        internal const val OR = "or"
        internal const val TOOLTIP = "tooltip-android"
    }

    public companion object {
        @Volatile
        private var INSTANCE: LocaleService? = null

        @JvmStatic
        @JvmOverloads
        @Suppress("DEPRECATION")
        public fun createInstance(
            context: Context,
            ownIdCore: OwnIdCore,
            handler: Handler? = Handler(Looper.getMainLooper())
        ): LocaleService = INSTANCE ?: synchronized(this) {
            if (INSTANCE != null) return@synchronized INSTANCE!!

            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
                .callTimeout(30, TimeUnit.SECONDS)
                .cache(Cache(directory = File(context.cacheDir, NETWORK_CACHE_DIR), maxSize = 5L * 1024L * 1024L))
                .build()

            return LocaleService(client, ownIdCore, handler).also { instance ->
                INSTANCE = instance
                instance.logD("Instance created. App locale: ${context.resources.configuration.locale}", ownIdCore)
            }
        }

        @JvmStatic
        @JvmSynthetic
        @InternalOwnIdAPI
        internal fun getInstance(): LocaleService =
            requireNotNull(INSTANCE) { "No DynamicLocale available. Check if OwnId instance created." }

        private const val NETWORK_CACHE_DIR: String = "ownid_locales_cache"
        private const val LOCALE_CACHE_TIME: Long = 1000L * 60L * 10L // 10 Minutes
        private const val LOCALE_CACHE_DIR: String = "ownid_locales"

        private val localFallback: Map<String, Int> = mapOf(
            Key.SKIP_PASSWORD to R.string.com_ownid_sdk_skip_password,
            Key.OR to R.string.com_ownid_sdk_or,
            Key.TOOLTIP to R.string.com_ownid_sdk_tooltip,
        )
    }

    internal interface LocaleUpdateListener {
        @MainThread
        fun onLocaleUpdated()
    }

    private val updateListenerSet = mutableSetOf<LocaleUpdateListener>()

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
    @Suppress("DEPRECATION")
    internal fun getString(context: Context, key: String): String {
        val selectedLocale = serverLocales.selectLocale(context.resources.configuration.locale)

        if (serverLocales.containsLocaleOrDefault(selectedLocale).not()) {
            return context.getString(localFallback.getValue(key))
        }

        val localeData = serverLocales.getCachedLocaleData(selectedLocale, localeCache)

        if (localeData == null || localeData.isExpired()) {
            updateLocale(selectedLocale)
        }

        return when {
            localeData == null -> {
                context.getString(localFallback.getValue(key))
            }
            localeData.hasString(key).not() -> {
                logW("No translation found for locale: `${selectedLocale.serverTag}` key: `$key`", ownIdCore)
                context.getString(localFallback.getValue(key))
            }
            else -> {
                localeData.getString(key)
            }
        }
    }

    private data class SdkLocale(val serverTag: String, private val locale: Locale) {
        companion object {
            @JvmStatic
            internal val DEFAULT = SdkLocale(Locale.ENGLISH.language, Locale.ENGLISH)

            @JvmStatic
            internal fun fromTag(serverTag: String): SdkLocale = SdkLocale(serverTag, Locale.forLanguageTag(serverTag))
        }

        val cacheKey: String
            get() = serverTag.lowercase(Locale.ROOT)

        fun getServerUrl(configuration: Configuration): HttpUrl = configuration.getLocaleUrl(serverTag)

        fun isSameLocale(locale: Locale): Boolean = this.locale.equals(locale)
    }

    private data class ServerLocaleSet(private val serverTagSet: Set<String>, private val timeStamp: Long) {
        companion object {
            private const val JSON_KEY: String = "langs"

            @JvmStatic
            internal val EMPTY = ServerLocaleSet(emptySet(), 0)

            @JvmStatic
            @Throws(JSONException::class)
            internal fun fromJson(jsonString: String, timeStamp: Long = System.currentTimeMillis()): ServerLocaleSet {
                val locales = JSONObject(jsonString).getJSONArray(JSON_KEY)
                val localesSet = List(locales.length()) { locales.getString(it) }.toSet()
                return ServerLocaleSet(localesSet, timeStamp)
            }

            @JvmStatic
            internal fun fromCache(cache: DiskLruCache): ServerLocaleSet =
                CachedString.get(JSON_KEY, cache)?.let { fromJson(it.data, it.timeStamp) } ?: EMPTY
        }

        fun saveToCache(cache: DiskLruCache) {
            val data = JSONObject().put(JSON_KEY, JSONArray(serverTagSet)).toString()
            CachedString(timeStamp, data).put(JSON_KEY, cache)
        }

        fun containsLocaleOrDefault(sdkLocale: SdkLocale): Boolean =
            serverTagSet.contains(sdkLocale.serverTag) || serverTagSet.contains(SdkLocale.DEFAULT.serverTag)

        fun getCachedLocaleData(sdkLocale: SdkLocale, cache: DiskLruCache): SdkLocaleData? {
            return when {
                serverTagSet.contains(sdkLocale.serverTag) -> SdkLocaleData.fromCache(sdkLocale, cache)
                serverTagSet.contains(SdkLocale.DEFAULT.serverTag) -> SdkLocaleData.fromCache(SdkLocale.DEFAULT, cache)
                else -> null
            }
        }

        fun selectLocale(currentAppLocale: Locale): SdkLocale {
            val sdkLocaleMap = serverTagSet.map { SdkLocale.fromTag(it) }

            val appLocale = currentAppLocale.run { Locale(language, country) }
            val supportedLocale = sdkLocaleMap.firstOrNull { it.isSameLocale(appLocale) }

            val appLocaleLanguage = Locale(appLocale.language)
            val supportedLocaleLanguage = sdkLocaleMap.firstOrNull { it.isSameLocale(appLocaleLanguage) }

            return supportedLocale ?: supportedLocaleLanguage ?: SdkLocale.DEFAULT
        }
    }

    private data class SdkLocaleData(
        val sdkLocale: SdkLocale, private val timeStamp: Long, private val data: JSONObject
    ) {
        companion object {
            @JvmStatic
            internal fun fromCache(sdkLocale: SdkLocale, cache: DiskLruCache): SdkLocaleData? {
                val cachedData = CachedString.get(sdkLocale.cacheKey, cache) ?: return null
                return SdkLocaleData(sdkLocale, cachedData.timeStamp, JSONObject(cachedData.data))
            }
        }

        fun saveToCache(cache: DiskLruCache) {
            CachedString(timeStamp, data.toString()).put(sdkLocale.cacheKey, cache)
        }

        fun hasString(key: String): Boolean = data.has(key)

        fun getString(key: String): String = data.getString(key)

        fun isExpired(): Boolean = System.currentTimeMillis() - timeStamp > LOCALE_CACHE_TIME
    }

    private val localeCache = DiskLruCache(
        fileSystem = FileSystem.SYSTEM,
        directory = File(ownIdCore.configuration.cacheDir, LOCALE_CACHE_DIR),
        appVersion = 1,
        valueCount = 1,
        maxSize = 5L * 1024L * 1024L,
        taskRunner = TaskRunner.INSTANCE
    )

    private var serverLocales: ServerLocaleSet
    private val requestsInProgress = Collections.synchronizedSet<String>(mutableSetOf())

    init {
        localeCache.initialize()
        serverLocales = ServerLocaleSet.fromCache(localeCache)
        updateLocaleList()
    }

    @Synchronized
    private fun updateLocaleList() {
        if (requestsInProgress.contains(ownIdCore.configuration.ownIdLocaleListUrl.toString())) return
        requestsInProgress.add(ownIdCore.configuration.ownIdLocaleListUrl.toString())

        val request: Request = Request.Builder()
            .url(ownIdCore.configuration.ownIdLocaleListUrl)
            .header("X-API-Version", Configuration.X_API_VERSION)
            .header("User-Agent", ownIdCore.configuration.userAgent)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                this@LocaleService.logE("updateLocaleList.onFailure", e, ownIdCore)
                requestsInProgress.remove(ownIdCore.configuration.ownIdLocaleListUrl.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.use {
                    if (response.isSuccessful.not()) {
                        val e = OwnIdException("Server response: ${response.code} - ${response.message}")
                        this@LocaleService.logE("updateLocaleList.onResponse", e, ownIdCore)
                        requestsInProgress.remove(ownIdCore.configuration.ownIdLocaleListUrl.toString())
                        return
                    }

                    response.body!!.string()
                }

                runOnHandler {
                    runCatching {
                        serverLocales = ServerLocaleSet.fromJson(jsonString).apply { saveToCache(localeCache) }
                        this@LocaleService.logV("updateLocaleList.onResponse: Ok", ownIdCore)
                        updateListenerSet.forEach { listener -> listener.onLocaleUpdated() }
                    }.onFailure {
                        this@LocaleService.logE("updateLocaleList.onResponse", it, ownIdCore)
                    }
                    requestsInProgress.remove(ownIdCore.configuration.ownIdLocaleListUrl.toString())
                }
            }
        })
    }

    @MainThread
    private fun updateLocale(sdkLocale: SdkLocale) {
        val localeUrl = sdkLocale.getServerUrl(ownIdCore.configuration)
        if (requestsInProgress.contains(localeUrl.toString())) return
        requestsInProgress.add(localeUrl.toString())

        val request: Request = Request.Builder()
            .url(localeUrl)
            .header("X-API-Version", Configuration.X_API_VERSION)
            .header("User-Agent", ownIdCore.configuration.userAgent)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                this@LocaleService.logE("updateLocale.onFailure: $sdkLocale", e, ownIdCore)
                requestsInProgress.remove(localeUrl.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.use {
                    if (response.isSuccessful.not()) {
                        val e = OwnIdException("Server response: ${response.code} - ${response.message}")
                        this@LocaleService.logE("updateLocale.onResponse: $sdkLocale", e, ownIdCore)
                        requestsInProgress.remove(localeUrl.toString())
                        return
                    }

                    response.body!!.string()
                }

                runOnHandler {
                    runCatching {
                        SdkLocaleData(sdkLocale, System.currentTimeMillis(), JSONObject(jsonString))
                            .apply { saveToCache(localeCache) }
                        this@LocaleService.logV("updateLocale.onResponse: $sdkLocale", ownIdCore)
                        updateListenerSet.forEach { listener -> listener.onLocaleUpdated() }
                    }.onFailure {
                        this@LocaleService.logE("updateLocale.onResponse: $sdkLocale", it, ownIdCore)
                    }
                    requestsInProgress.remove(localeUrl.toString())
                }
            }
        })
    }

    private fun runOnHandler(action: () -> Unit) {
        handler?.post(action) ?: action()
    }

    private data class CachedString(val timeStamp: Long, val data: String) {
        companion object {
            @JvmStatic
            internal fun get(key: String, cache: DiskLruCache): CachedString? {
                val snapshot: DiskLruCache.Snapshot = try {
                    cache[key] ?: return null
                } catch (_: IOException) {
                    return null
                }

                return try {
                    snapshot.getSource(0).use { source ->
                        source.buffer().run { CachedString(readDecimalLong(), readUtf8()) }
                    }
                } catch (_: IOException) {
                    snapshot.closeQuietly()
                    null
                } catch (_: JSONException) {
                    snapshot.closeQuietly()
                    null
                }
            }
        }

        fun put(key: String, cache: DiskLruCache): Boolean {
            var editor: DiskLruCache.Editor? = null
            try {
                editor = cache.edit(key) ?: return false
                editor.newSink(0).buffer().use { sink ->
                    sink.writeDecimalLong(timeStamp)
                        .writeByte('\n'.code)
                        .writeUtf8(data)
                }
                editor.commit()
                return true
            } catch (_: IOException) {
                try {
                    editor?.abort()
                } catch (_: IOException) {
                }
            }
            return false
        }
    }
}