package com.ownid.sdk.ui.locale

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.OwnIdConfiguration
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.impl.SerializationImpl
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.LoginId
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import com.ownid.sdk.ui.capability.BoostWidgetStringsParams
import com.ownid.sdk.ui.capability.BoostWidgetStringsProvider
import com.ownid.sdk.ui.capability.EmailVerificationStrings
import com.ownid.sdk.ui.capability.EmailVerificationStringsParams
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.LoginIdCollectStrings
import com.ownid.sdk.ui.capability.LoginIdCollectStringsParams
import com.ownid.sdk.ui.capability.LoginIdCollectStringsProvider
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import com.ownid.sdk.ui.capability.PhoneVerificationStringsParams
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider
import com.ownid.sdk.ui.capability.impl.BoostWidgetStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.BoostWidgetStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.EmailVerificationStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.EmailVerificationStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.LoginIdCollectStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.LoginIdCollectStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.PhoneVerificationStringsEmbeddedRepositoryImpl
import com.ownid.sdk.ui.capability.impl.PhoneVerificationStringsServerRepositoryImpl
import com.ownid.sdk.ui.capability.impl.StringsProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: LOC-RUNTIME-010, LOC-RUNTIME-020, LOC-RUNTIME-030, LOC-RUNTIME-040, LOC-RUNTIME-050, STRINGS-RUNTIME-010
internal class ServerLocaleRuntimeContractTest {

    private val app: Application
        get() = RuntimeEnvironment.getApplication()

    private val serialization = SerializationImpl()

    @Test
    fun persistedServerLocaleContentIsReadAcrossRecreatedStores(): Unit = runTest {
        val suffix = uniqueSuffix()
        val languageTag = LanguageTag("fr", "CA")
        val firstOwner = newOwnerScope()
        try {
            dataStore(suffix, firstOwner).setContent(
                languageTag,
                ServerLocaleContent(
                    languageTag = languageTag,
                    content = localeJson("""{"widgets":{"sbs-button":{"skipPassword":"Continuer"}}}""")
                )
            )
        } finally {
            firstOwner.cancel()
        }
        advanceUntilIdle()

        val recreatedOwner = newOwnerScope()
        try {
            val content = dataStore(suffix, recreatedOwner).getDataSource(languageTag).filterNotNull().first()

            assertThat(content.languageTag).isEqualTo(languageTag)
            assertThat(content.getString("widgets", "sbs-button", "skipPassword")).isEqualTo("Continuer")
        } finally {
            recreatedOwner.cancel()
        }
    }

    @Test
    fun corruptedServerLocaleCacheIsIgnoredAndRewrittenByLaterContent(): Unit = runTest {
        val suffix = uniqueSuffix()
        val languageTag = LanguageTag("fr", "CA")
        val firstOwner = newOwnerScope()
        val file = try {
            dataStore(suffix, firstOwner).setContent(
                languageTag,
                ServerLocaleContent(languageTag = languageTag, content = localeJson("""{"title":"Stored"}"""))
            )
            cacheFile(suffix).also { assertThat(it.exists()).isTrue() }
        } finally {
            firstOwner.cancel()
        }
        advanceUntilIdle()
        file.writeBytes(byteArrayOf(0x00, 0x45, 0x12, 0x7f))

        val logEntries = mutableListOf<LogEntry>()
        val ownerAfterCorruption = newOwnerScope()
        try {
            val afterCorruption = dataStore(suffix, ownerAfterCorruption, logger = recordingRouter(logEntries))

            assertThat(afterCorruption.getDataSource(languageTag).first()).isNull()
            assertThat(logEntries.any { it.level == OwnIdLogger.Level.Warn && it.message.contains("DataStore corrupted") }).isTrue()

            afterCorruption.setContent(
                languageTag,
                ServerLocaleContent(languageTag = languageTag, content = localeJson("""{"title":"Rewritten"}"""))
            )
            assertThat(afterCorruption.getDataSource(languageTag).filterNotNull().first().getString("title")).isEqualTo("Rewritten")
        } finally {
            ownerAfterCorruption.cancel()
        }
    }

    @Test
    fun missingLocaleRefreshesAndPublishesFetchedContent(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"title":"Fetched title"}"""))
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("fr", "CA")
            val provider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)

            try {
                val firstContent = provider.getDataSource(languageTag).first()
                assertThat(firstContent).isNull()

                val updated = async { provider.getDataSource(languageTag).filterNotNull().first() }
                runCurrent()
                val requestPath = server.takeRequestPath()

                assertThat(requestPath).isEqualTo("/i18n/fr-CA/mobile-sdk.json")
                assertThat(updated.await().getString("title")).isEqualTo("Fetched title")
            } finally {
                cancelAndDrain(provider)
            }
        }
    }

    @Test
    fun staleLocaleRefreshKeepsOldContentVisibleUntilSuccess(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"title":"Fresh title"}"""))
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("fr", "CA")
            writeCachedContent(
                suffix = suffix,
                languageTag = languageTag,
                content = ServerLocaleContent(
                    languageTag = languageTag,
                    content = localeJson("""{"title":"Cached title"}"""),
                    timeStamp = 0L
                )
            )

            val provider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)
            val dataSources = provider.getDataSource(languageTag).filterNotNull()

            try {
                val cached = dataSources.first()
                assertThat(cached.getString("title")).isEqualTo("Cached title")

                val fresh = async { dataSources.drop(1).first() }
                runCurrent()
                val requestPath = server.takeRequestPath()

                assertThat(requestPath).isEqualTo("/i18n/fr-CA/mobile-sdk.json")
                assertThat(fresh.await().getString("title")).isEqualTo("Fresh title")
            } finally {
                cancelAndDrain(provider)
            }
        }
    }

    @Test
    fun missingLocale404WritesNilPlaceholderWithBackoff(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"missing"}"""))
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("fr", "CA")
            val nowMillis = 1_700_000_000_000L
            val provider = serverLocaleProvider(
                suffix = suffix,
                languageTag = languageTag,
                server = server,
                clock = ServerLocaleDataSourceProviderImpl.Clock { nowMillis }
            )

            val placeholderEmission = async { provider.getDataSource(languageTag).drop(1).first() }
            runCurrent()
            assertThat(server.takeRequestPath()).isEqualTo("/i18n/fr-CA/mobile-sdk.json")
            assertThat(placeholderEmission.await()).isNull()
            advanceUntilIdle()
            cancelAndDrain(provider)

            val placeholder = readStoredContent(suffix, languageTag)

            assertThat(placeholder).isNotNull()
            assertThat(placeholder?.languageTag).isEqualTo(languageTag)
            assertThat(placeholder?.content).isNull()
            assertThat(placeholder?.timeStamp).isEqualTo(nowMillis)
            assertThat(placeholder?.backoffUntil).isEqualTo(nowMillis + TimeUnit.HOURS.toMillis(1))
            assertThat(placeholder?.isExpired(nowMillis)).isFalse()
        }
    }

    @Test
    fun networkFailurePreservesCachedLocaleContent(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("fr", "CA")
            val cachedContent = ServerLocaleContent(
                languageTag = languageTag,
                content = localeJson("""{"title":"Cached title"}"""),
                timeStamp = 0L
            )
            writeCachedContent(suffix = suffix, languageTag = languageTag, content = cachedContent)
            val provider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)

            val cached = provider.getDataSource(languageTag).filterNotNull().first()
            assertThat(cached.getString("title")).isEqualTo("Cached title")
            runCurrent()
            assertThat(server.takeRequestPath()).isEqualTo("/i18n/fr-CA/mobile-sdk.json")
            advanceUntilIdle()
            cancelAndDrain(provider)

            val stored = readStoredContent(suffix, languageTag)
            assertThat(stored).isEqualTo(cachedContent)
        }
    }

    @Test
    fun concurrentSameTagSubscribersShareOneFetchAndBothObserveUpdate(): Unit = runTest {
        MockWebServer().use { server ->
            val requestStarted = CountDownLatch(1)
            val releaseResponse = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requestStarted.countDown()
                    return if (releaseResponse.await(1, TimeUnit.SECONDS)) {
                        MockResponse().setBody("""{"title":"Shared title"}""")
                    } else {
                        MockResponse().setResponseCode(500).setBody("""{"error":"timeout"}""")
                    }
                }
            }
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("fr", "CA")
            val provider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)

            try {
                val firstSubscriber = async { provider.getDataSource(languageTag).filterNotNull().first() }
                val secondSubscriber = async { provider.getDataSource(languageTag).filterNotNull().first() }
                runCurrent()

                assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue()
                runCurrent()
                assertThat(server.takeRequestPath()).isEqualTo("/i18n/fr-CA/mobile-sdk.json")
                assertThat(server.requestCount).isEqualTo(1)

                releaseResponse.countDown()
                assertThat(firstSubscriber.await().getString("title")).isEqualTo("Shared title")
                assertThat(secondSubscriber.await().getString("title")).isEqualTo("Shared title")
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                releaseResponse.countDown()
                cancelAndDrain(provider)
            }
        }
    }

    @Test
    fun localeHttpCacheIsScopedByEnvironmentAndAppId(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(cacheableLocaleResponse("""{"title":"UAT cached"}"""))
            server.enqueue(cacheableLocaleResponse("""{"title":"Prod fresh"}"""))
            server.enqueue(cacheableLocaleResponse("""{"title":"Other app fresh"}"""))
            server.start()
            val appId = UUID.randomUUID().toString().replace("-", "")
            val languageTag = LanguageTag("fr", "CA")
            val uatSuffix = "uat_$appId"

            val uatProvider = serverLocaleProvider(suffix = uatSuffix, appId = appId, languageTag = languageTag, server = server)
            assertThat(fetchMissingLocale(uatProvider, languageTag).getString("title")).isEqualTo("UAT cached")
            assertThat(server.requestCount).isEqualTo(1)
            cancelAndDrain(uatProvider)
            cacheFile(uatSuffix).delete()

            val sameScopeProvider = serverLocaleProvider(suffix = uatSuffix, appId = appId, languageTag = languageTag, server = server)
            assertThat(fetchMissingLocale(sameScopeProvider, languageTag).getString("title")).isEqualTo("UAT cached")
            assertThat(server.requestCount).isEqualTo(1)
            cancelAndDrain(sameScopeProvider)

            val prodSuffix = "prod_$appId"
            val prodProvider = serverLocaleProvider(
                suffix = prodSuffix,
                appId = appId,
                env = OwnIdConfiguration.Env.Prod,
                languageTag = languageTag,
                server = server
            )
            assertThat(fetchMissingLocale(prodProvider, languageTag).getString("title")).isEqualTo("Prod fresh")
            assertThat(server.requestCount).isEqualTo(2)
            cancelAndDrain(prodProvider)

            val otherAppId = UUID.randomUUID().toString().replace("-", "")
            val otherAppProvider = serverLocaleProvider(suffix = "uat_$otherAppId", appId = otherAppId, languageTag = languageTag, server = server)
            try {
                assertThat(fetchMissingLocale(otherAppProvider, languageTag).getString("title")).isEqualTo("Other app fresh")
                assertThat(server.requestCount).isEqualTo(3)
            } finally {
                cancelAndDrain(otherAppProvider)
            }
        }
    }

    @Test
    fun nonSuccessLocaleResponsesAreNotStoredInHttpCache(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(cacheableLocaleResponse("""{"error":"missing"}""").setResponseCode(404))
            server.enqueue(cacheableLocaleResponse("""{"title":"Fetched after miss"}"""))
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("fr", "CA")

            val firstProvider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)
            assertThat(firstProvider.getDataSource(languageTag).first()).isNull()
            runCurrent()
            assertThat(server.takeRequestPath()).isEqualTo("/i18n/fr-CA/mobile-sdk.json")
            advanceUntilIdle()
            cancelAndDrain(firstProvider)
            cacheFile(suffix).delete()

            val secondProvider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)
            try {
                assertThat(fetchMissingLocale(secondProvider, languageTag).getString("title")).isEqualTo("Fetched after miss")
                assertThat(server.requestCount).isEqualTo(2)
            } finally {
                cancelAndDrain(secondProvider)
            }
        }
    }

    @Test
    fun boostWidgetStringsReadCachedLocaleDataAndUpdateActiveConsumersAfterRefresh(): Unit = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "widgets": {
                        "sbs-button": {
                          "skipPassword": "Fresh skip",
                          "or": "Fresh or"
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("en", "")
            writeCachedContent(
                suffix = suffix,
                languageTag = languageTag,
                content = ServerLocaleContent(
                    languageTag = languageTag,
                    content = localeJson(
                        """
                        {
                          "widgets": {
                            "sbs-button": {
                              "skipPassword": "Cached skip",
                              "or": "Cached or"
                            }
                          }
                        }
                        """.trimIndent()
                    ),
                    timeStamp = 0L
                )
            )
            val localeProvider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)
            val stringsProvider = boostWidgetStringsProvider(languageTag, localeProvider)
            val strings = stringsProvider.getStrings(BoostWidgetStringsParams())

            try {
                val cached = strings.filterNotNull().first()
                assertThat(cached).isEqualTo(BoostWidgetStrings(skipPassword = "Cached skip", or = "Cached or"))

                val fresh = async { strings.filterNotNull().drop(1).first() }
                runCurrent()
                val requestPath = server.takeRequestPath()
                advanceUntilIdle()

                assertThat(requestPath).isEqualTo("/i18n/en/mobile-sdk.json")
                assertThat(fresh.await()).isEqualTo(BoostWidgetStrings(skipPassword = "Fresh skip", or = "Fresh or"))
            } finally {
                cancelAndDrain(localeProvider)
            }
        }
    }

    @Test
    fun operationStringsReadCachedLocaleDataAndUpdateActiveConsumersAfterRefresh(): Unit = runTest {
        MockWebServer().use { server ->
            val requestStarted = CountDownLatch(1)
            val releaseResponse = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requestStarted.countDown()
                    return if (releaseResponse.await(1, TimeUnit.SECONDS)) {
                        MockResponse().setBody(operationStringsLocaleJson("Fresh"))
                    } else {
                        MockResponse().setResponseCode(500).setBody("""{"error":"timeout"}""")
                    }
                }
            }
            server.start()
            val suffix = uniqueSuffix()
            val languageTag = LanguageTag("en", "")
            writeCachedContent(
                suffix = suffix,
                languageTag = languageTag,
                content = ServerLocaleContent(
                    languageTag = languageTag,
                    content = localeJson(operationStringsLocaleJson("Cached")),
                    timeStamp = 0L
                )
            )
            val localeProvider = serverLocaleProvider(suffix = suffix, languageTag = languageTag, server = server)
            val loginStrings = loginIdCollectStringsProvider(languageTag, localeProvider)
                .getStrings(LoginIdCollectStringsParams(listOf(LoginId.Type.Email)))
            val emailStrings = emailVerificationStringsProvider(languageTag, localeProvider)
                .getStrings(EmailVerificationStringsParams())
            val phoneStrings = phoneVerificationStringsProvider(languageTag, localeProvider)
                .getStrings(PhoneVerificationStringsParams())

            try {
                assertLoginStrings(loginStrings.filterNotNull().first())
                assertEmailStrings(emailStrings.filterNotNull().first())
                assertPhoneStrings(phoneStrings.filterNotNull().first())

                val freshLogin = async { loginStrings.filterNotNull().drop(1).first() }
                val freshEmail = async { emailStrings.filterNotNull().drop(1).first() }
                val freshPhone = async { phoneStrings.filterNotNull().drop(1).first() }
                runCurrent()
                assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue()
                val requestPath = server.takeRequestPath()
                releaseResponse.countDown()
                advanceUntilIdle()

                assertThat(requestPath).isEqualTo("/i18n/en/mobile-sdk.json")
                assertThat(freshLogin.await().title).isEqualTo("Fresh login title")
                assertThat(freshEmail.await().message).isEqualTo("Fresh email message")
                assertThat(freshPhone.await().description).isEqualTo("Fresh phone description")
            } finally {
                releaseResponse.countDown()
                cancelAndDrain(localeProvider)
            }
        }
    }

    private suspend fun TestScope.writeCachedContent(
        suffix: String,
        languageTag: LanguageTag,
        content: ServerLocaleContent
    ) {
        val owner = newOwnerScope()
        try {
            dataStore(suffix, owner).setContent(languageTag, content)
        } finally {
            owner.cancel()
        }
        advanceUntilIdle()
    }

    private suspend fun TestScope.readStoredContent(suffix: String, languageTag: LanguageTag): ServerLocaleContent? {
        val owner = newOwnerScope()
        val content = try {
            dataStore(suffix, owner).getContent(languageTag)
        } finally {
            owner.cancel()
        }
        advanceUntilIdle()
        return content
    }

    private fun TestScope.cancelAndDrain(provider: ServerLocaleDataSourceProviderImpl) {
        provider.cancel()
        advanceUntilIdle()
    }

    private suspend fun TestScope.fetchMissingLocale(
        provider: ServerLocaleDataSource.Provider,
        languageTag: LanguageTag
    ): ServerLocaleDataSource {
        val content = async { provider.getDataSource(languageTag).filterNotNull().first() }
        runCurrent()
        return content.await()
    }

    private fun TestScope.serverLocaleProvider(
        suffix: String,
        appId: String = suffix.removePrefix("uat_"),
        env: OwnIdConfiguration.Env = OwnIdConfiguration.Env.Uat,
        languageTag: LanguageTag,
        server: MockWebServer,
        clock: ServerLocaleDataSourceProviderImpl.Clock = ServerLocaleDataSourceProviderImpl.Clock.Real,
    ): ServerLocaleDataSourceProviderImpl =
        ServerLocaleDataSourceProviderImpl(
            context = app,
            configuration = TestConfiguration(appId = appId, env = env, rootUrl = server.url("/").toString()),
            okHttpClient = OkHttpClient(),
            languageTagsProvider = FakeLanguageTagsProvider(languageTag),
            serialization = serialization,
            dispatcher = StandardTestDispatcher(testScheduler),
            instanceJob = requireNotNull(backgroundScope.coroutineContext[Job]),
            logger = null,
            clock = clock,
            startBackgroundWork = false
        )

    private fun TestScope.boostWidgetStringsProvider(
        languageTag: LanguageTag,
        localeProvider: ServerLocaleDataSource.Provider
    ): BoostWidgetStringsProvider =
        object : StringsProviderImpl<BoostWidgetStrings, BoostWidgetStringsParams>(
            languageTagsProvider = FakeLanguageTagsProvider(languageTag),
            embeddedRepository = BoostWidgetStringsEmbeddedRepositoryImpl(),
            serverRepository = BoostWidgetStringsServerRepositoryImpl(localeProvider),
            scope = backgroundScope,
            logger = null
        ), BoostWidgetStringsProvider {}

    private fun TestScope.loginIdCollectStringsProvider(
        languageTag: LanguageTag,
        localeProvider: ServerLocaleDataSource.Provider
    ): LoginIdCollectStringsProvider =
        object : StringsProviderImpl<LoginIdCollectStrings, LoginIdCollectStringsParams>(
            languageTagsProvider = FakeLanguageTagsProvider(languageTag),
            embeddedRepository = LoginIdCollectStringsEmbeddedRepositoryImpl(TestLocalInfo),
            serverRepository = LoginIdCollectStringsServerRepositoryImpl(TestLocalInfo, localeProvider),
            scope = backgroundScope,
            logger = null
        ), LoginIdCollectStringsProvider {}

    private fun TestScope.emailVerificationStringsProvider(
        languageTag: LanguageTag,
        localeProvider: ServerLocaleDataSource.Provider
    ): EmailVerificationStringsProvider =
        object : StringsProviderImpl<EmailVerificationStrings, EmailVerificationStringsParams>(
            languageTagsProvider = FakeLanguageTagsProvider(languageTag),
            embeddedRepository = EmailVerificationStringsEmbeddedRepositoryImpl(),
            serverRepository = EmailVerificationStringsServerRepositoryImpl(localeProvider),
            scope = backgroundScope,
            logger = null
        ), EmailVerificationStringsProvider {}

    private fun TestScope.phoneVerificationStringsProvider(
        languageTag: LanguageTag,
        localeProvider: ServerLocaleDataSource.Provider
    ): PhoneVerificationStringsProvider =
        object : StringsProviderImpl<PhoneVerificationStrings, PhoneVerificationStringsParams>(
            languageTagsProvider = FakeLanguageTagsProvider(languageTag),
            embeddedRepository = PhoneVerificationStringsEmbeddedRepositoryImpl(),
            serverRepository = PhoneVerificationStringsServerRepositoryImpl(localeProvider),
            scope = backgroundScope,
            logger = null
        ), PhoneVerificationStringsProvider {}

    private fun assertLoginStrings(actual: LoginIdCollectStrings) {
        val prefix = "Cached"
        assertThat(actual.title).isEqualTo("$prefix login title")
        assertThat(actual.message).isEqualTo("$prefix login message")
        assertThat(actual.placeholder).isEqualTo("$prefix login placeholder")
        assertThat(actual.cancel).isEqualTo("$prefix login cancel")
        assertThat(actual.cta).isEqualTo("$prefix login cta")
        assertThat(actual.error).isEqualTo("$prefix login error")
    }

    private fun assertEmailStrings(actual: EmailVerificationStrings) {
        val prefix = "Cached"
        assertThat(actual.title).isEqualTo("$prefix email title")
        assertThat(actual.message).isEqualTo("$prefix email message")
        assertThat(actual.description).isEqualTo("$prefix email description")
        assertThat(actual.resend).isEqualTo("$prefix email resend")
        assertThat(actual.cancel).isEqualTo("$prefix email cancel")
        assertThat(actual.notYou).isEqualTo("$prefix email not you")
    }

    private fun assertPhoneStrings(actual: PhoneVerificationStrings) {
        val prefix = "Cached"
        assertThat(actual.title).isEqualTo("$prefix phone title")
        assertThat(actual.message).isEqualTo("$prefix phone message")
        assertThat(actual.description).isEqualTo("$prefix phone description")
        assertThat(actual.resend).isEqualTo("$prefix phone resend")
        assertThat(actual.cancel).isEqualTo("$prefix phone cancel")
        assertThat(actual.notYou).isEqualTo("$prefix phone not you")
    }

    private fun TestScope.newOwnerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun dataStore(suffix: String, ownerScope: CoroutineScope, logger: OwnIdLogRouter? = null): ServerLocaleDataStore =
        ServerLocaleDataStore(context = app, scope = ownerScope, suffix = suffix, serialization = serialization, logger = logger)

    private fun cacheFile(suffix: String): File = File(app.cacheDir, "ownid/locales/$suffix.preferences_pb")

    private fun cacheableLocaleResponse(body: String): MockResponse = MockResponse()
        .setHeader("Cache-Control", "max-age=3600")
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun MockWebServer.takeRequestPath(): String? = takeRequest(1, TimeUnit.SECONDS)?.path

    private fun localeJson(rawJson: String): JsonObject = serialization.json.parseToJsonElement(rawJson).jsonObject

    private fun operationStringsLocaleJson(prefix: String): String =
        """
        {
          "steps": {
            "login-id-collect": {
              "email": {
                "title": "$prefix login title",
                "message": "$prefix login message",
                "placeholder": "$prefix login placeholder",
                "cancel": "$prefix login cancel",
                "cta": "$prefix login cta",
                "error": "$prefix login error"
              }
            },
            "otp": {
              "verify": {
                "email": {
                  "title": "$prefix email title"
                },
                "sms": {
                  "title": "$prefix phone title"
                }
              },
              "email": {
                "verify": {
                  "message": "$prefix email message",
                  "description": "$prefix email description",
                  "resend": "$prefix email resend",
                  "cancel": "$prefix email cancel",
                  "not-you": "$prefix email not you"
                }
              },
              "sms": {
                "verify": {
                  "message": "$prefix phone message",
                  "description": "$prefix phone description",
                  "resend": "$prefix phone resend",
                  "cancel": "$prefix phone cancel",
                  "not-you": "$prefix phone not you"
                }
              }
            }
          }
        }
        """.trimIndent()

    private fun uniqueSuffix(): String = "uat_${UUID.randomUUID().toString().replace("-", "")}"

    private fun recordingRouter(entries: MutableList<LogEntry>): OwnIdLogRouter {
        val logger = object : OwnIdLogger {
            override val level: OwnIdLogger.Level = OwnIdLogger.Level.Verbose
            override val tag: String = "OwnID-Locale-Test"

            override fun log(level: OwnIdLogger.Level, className: String, message: String, cause: Throwable?) {
                entries += LogEntry(level = level, message = message)
            }
        }
        return OwnIdLogRouter(ownIdLoggerProvider = { logger }, serverLoggersProvider = { emptyList() })
    }

    private data class LogEntry(
        val level: OwnIdLogger.Level,
        val message: String
    )

    private class FakeLanguageTagsProvider(initialLanguageTag: LanguageTag) : LanguageTagsProvider {
        private val tags = MutableStateFlow(listOf(initialLanguageTag))

        override fun setLanguageTags(tags: List<String>): Unit = error("Unexpected language update")

        override val languageTagsFlow: StateFlow<List<LanguageTag>>
            get() = tags
    }

    private object TestLocalInfo : LocalInfo {
        override val modules: List<Pair<String, String>> = emptyList()
        override val packageName: String = "com.ownid.test"
        override val appVersion: String = "1.0"
        override val userAgent: String = "OwnID-Test/1.0"
        override val correlationId: String = "correlation-id"
        override val isDebuggable: Boolean = true
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean? = null
        override val isFingerprintHardwarePresent: Boolean? = null
        override val isFaceHardwarePresent: Boolean? = null
        override val isIrisHardwarePresent: Boolean? = null
        override val isStrongBiometricEnabled: Boolean? = null
    }

    private data class TestConfiguration(
        override val appId: String,
        override val env: OwnIdConfiguration.Env,
        override val rootUrl: String,
    ) : OwnIdConfiguration {
        override val region: OwnIdConfiguration.Region = OwnIdConfiguration.Region.US
    }
}
