package com.ownid.sdk.ui.locale

import android.content.Context
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.Network
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.capability.impl.executeAsync
import com.ownid.sdk.env
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.runCatchingExceptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Default [ServerLocaleDataSource.Provider] backed by persisted locale entries and network refresh.
 *
 * Consumers subscribe per [LanguageTag] and receive the currently available server data source, or `null` when no usable
 * payload exists. Reads may trigger a background refresh while existing usable content remains available. The provider
 * also tracks the active primary language and refreshes the primary, language-only, and default fallback tags for the
 * instance.
 *
 * Refresh results are surfaced through the stream rather than thrown to callers. Missing server locales and suppressed
 * refresh failures are represented as `null` or as previously cached content when usable content exists. Network
 * failures do not clear existing content. Callers must continue to resolve embedded defaults for missing streams or
 * missing individual keys.
 */
@InternalOwnIdAPI
internal class ServerLocaleDataSourceProviderImpl(
    context: Context,
    configuration: OwnIdConfiguration,
    okHttpClient: OkHttpClient,
    private val languageTagsProvider: LanguageTagsProvider,
    private val serialization: Serialization,
    dispatcher: CoroutineDispatcher,
    instanceJob: Job,
    private val logger: OwnIdLogRouter?,
    private val clock: Clock = Clock.Real,
    private val startBackgroundWork: Boolean = true
) : ServerLocaleDataSource.Provider,
    CoroutineScope by CoroutineScope(dispatcher + SupervisorJob(instanceJob) + CoroutineName("OwnId.ServerLocaleDataSourceProvider")) {

    private val suffix = "${configuration.env().name.lowercase()}_${configuration.appId}"

    private val serverLocaleDataStore = ServerLocaleDataStore(context, this, suffix, serialization, logger)

    private val baseLocaleUrl = when (val rootUrl = configuration.rootUrl) {
        null -> "https://i18n.${configuration.env().name.lowercase()}.ownid.com".toHttpUrl()
        else -> rootUrl.toHttpUrl().newBuilder().addPathSegment("i18n").build()
    }

    private val okHttpClient = okHttpClient.newBuilder()
        .cache(Cache(directory = File(context.cacheDir, "ownid/locales/$suffix"), maxSize = 2L * 1024L * 1024L))
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                response.newBuilder().header("Cache-Control", "no-store").build()
            } else {
                response
            }
        }
        .build()

    private val updateMutex = Mutex()
    private val updateJobs = mutableMapOf<LanguageTag, Deferred<Unit>>()

    init {
        if (startBackgroundWork) {
            launch {
                delay(3.seconds)
                languageTagsProvider.languageTagsFlow
                    .map { tags -> tags.firstOrNull() ?: LanguageTag.DEFAULT }
                    .distinctUntilChanged()
                    .onEach { tag ->
                        setOf(tag, tag.toLanguageOnly(), LanguageTag.DEFAULT).forEach {
                            launch { triggerLocaleUpdate(it) }
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    /**
     * Returns a flow of locale data sources for [languageTag].
     *
     * The flow emits `null` when no locale JSON content exists for the tag. Expired or missing content triggers a
     * background refresh while any existing data source continues to be served until the persisted state changes.
     */
    override fun getDataSource(languageTag: LanguageTag): Flow<ServerLocaleDataSource?> =
        runCatchingExceptions { serverLocaleDataStore.getDataSource(languageTag) }
            .getOrDefault(flowOf(null))
            .map { serverLocaleContent ->
                if (shouldForceUpdate(serverLocaleContent)) {
                    launch { triggerLocaleUpdate(languageTag) }
                }

                serverLocaleContent?.content?.let { createDataSource(languageTag, serverLocaleContent) }
            }

    /** Starts a best-effort refresh for [languageTag] when the current entry should be refreshed. */
    private suspend fun triggerLocaleUpdate(languageTag: LanguageTag) {
        val existingContent = runCatchingExceptions {
            val existingContent = serverLocaleDataStore.getContent(languageTag)

            if (shouldForceUpdate(existingContent).not()) {
                logger?.logD(this, "triggerLocaleUpdate", "Skipping update for $languageTag")
                return
            }

            existingContent
        }.getOrNull()

        updateMutex.withLock {
            if (updateJobs.containsKey(languageTag)) {
                logger?.logD(this, "triggerLocaleUpdate", "Update job already in progress for $languageTag")
                return
            }

            updateJobs[languageTag] = async {
                try {
                    performLocaleUpdate(languageTag, existingContent)
                } finally {
                    withContext(NonCancellable) {
                        updateMutex.withLock { updateJobs.remove(languageTag) }
                    }
                }
            }
        }
    }

    /** Fetches locale JSON for [languageTag] and publishes either usable content, `null`, or the prior usable content. */
    private suspend fun performLocaleUpdate(languageTag: LanguageTag, content: ServerLocaleContent? = null) {
        val url = runCatchingExceptions { baseLocaleUrl.newBuilder().addPathSegment(languageTag.tagString).addPathSegment("mobile-sdk.json").build() }
            .onFailure { logger?.logW(this, "performLocaleUpdate", "Locale URL creation failed [$languageTag] => ${it.message}", it) }
            .getOrNull() ?: return

        when (val response = okHttpClient.newCall(Request.Builder().url(url).get().build()).executeAsync()) {
            is Network.Response.Success -> runCatchingExceptions {
                val jsonObject = serialization.decodeFromString(JsonObject.serializer(), response.body)
                val newContent = ServerLocaleContent(languageTag = languageTag, content = jsonObject)
                serverLocaleDataStore.setContent(languageTag, newContent)
                logger?.logV(this, "performLocaleUpdate", "Locale update success [$languageTag]")
            }.getOrElse {
                if (it is CancellationException) throw it
                logger?.logW(this, "performLocaleUpdate", "Locale update failed [$languageTag] ($url) => ${it.message}", it)
                suppressAfterError(languageTag, content)
            }

            is Network.Response.Fail.HttpError -> runCatchingExceptions {
                logger?.logW(this, "performLocaleUpdate", "Update failed [$languageTag] ($url) => ${response.statusCode}")
                if (response.statusCode == 404) {
                    val timeStamp = clock.currentTimeMillis()
                    serverLocaleDataStore.setContent(
                        languageTag,
                        ServerLocaleContent(
                            languageTag = languageTag,
                            content = null,
                            timeStamp = timeStamp,
                            backoffUntil = timeStamp + TimeUnit.HOURS.toMillis(1)
                        )
                    )
                } else {
                    suppressAfterError(languageTag, content)
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                logger?.logW(this, "performLocaleUpdate", "Locale data update failed [$languageTag] ($url) => ${it.message}", it)
            }

            is Network.Response.Fail.NetworkError ->
                logger?.logW(this, "performLocaleUpdate", "Update failed [$languageTag] ($url) => ${response.error}")

            is Network.Response.Fail.ResponseError -> runCatchingExceptions {
                logger?.logW(this, "performLocaleUpdate", "Update failed [$languageTag] ($url) => ${response.error}")
                suppressAfterError(languageTag, content)
            }.getOrElse {
                if (it is CancellationException) throw it
                logger?.logW(this, "performLocaleUpdate", "Locale data update failed [$languageTag] ($url) => ${it.message}", it)
            }
        }
    }

    @Throws
    private suspend fun suppressAfterError(languageTag: LanguageTag, content: ServerLocaleContent?) {
        val currentTimeMillis = clock.currentTimeMillis()
        val oldContent = content ?: serverLocaleDataStore.getContent(languageTag)
        val newContent = oldContent?.copy(timeStamp = currentTimeMillis, backoffUntil = null)
            ?: ServerLocaleContent(languageTag = languageTag, timeStamp = currentTimeMillis)
        serverLocaleDataStore.setContent(languageTag, newContent)
    }

    private fun createDataSource(languageTag: LanguageTag, serverLocaleContent: ServerLocaleContent): ServerLocaleDataSource =
        object : ServerLocaleDataSource {
            override val languageTag: LanguageTag = languageTag
            override fun getString(vararg key: String): String? =
                runCatchingExceptions { serverLocaleContent.getString(*key) }
                    .onFailure { logger?.logW(this, "getString", "Failed to get [$languageTag]@${key.toList()} => ${it.message}") }
                    .getOrNull()
        }

    /** Returns `true` when [content] is missing or cache-expired. */
    private fun shouldForceUpdate(content: ServerLocaleContent?): Boolean = content == null || content.isExpired(clock.currentTimeMillis())

    internal fun interface Clock {
        fun currentTimeMillis(): Long

        companion object {
            val Real: Clock = Clock { System.currentTimeMillis() }
        }
    }
}
