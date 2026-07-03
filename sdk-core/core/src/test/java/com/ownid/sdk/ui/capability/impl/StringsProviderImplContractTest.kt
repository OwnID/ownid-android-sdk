package com.ownid.sdk.ui.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.ui.capability.EmailVerificationStrings
import com.ownid.sdk.ui.capability.EmailVerificationStringsParams
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import com.ownid.sdk.ui.capability.PhoneVerificationStringsParams
import com.ownid.sdk.ui.capability.StringsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, InternalOwnIdAPI::class)
// Covers: LOC-010, LOC-020, LOC-030
internal class StringsProviderImplContractTest {

    @Test
    fun exactLanguageAndDefaultServerStringsMergeBeforeEmbeddedFallbacks() = runTest {
        val languageTagsProvider = FakeLanguageTagsProvider(LanguageTag("fr", "CA"))
        val serverRepository = FakeServerRepository().apply {
            flowFor(LanguageTag("fr", "CA")).value = mapOf(
                "title" to "Exact title",
                "shared" to "Exact shared"
            )
            flowFor(LanguageTag("fr", "")).value = mapOf(
                "title" to "Language title",
                "message" to "Language message"
            )
            flowFor(LanguageTag.DEFAULT).value = mapOf(
                "message" to "Default message",
                "cancel" to "Default cancel"
            )
        }
        val provider = TestStringsProvider(languageTagsProvider, TestEmbeddedRepository, serverRepository, backgroundScope)

        val result = async { provider.getStrings(TestParams).filterNotNull().first() }
        runCurrent()

        assertThat(serverRepository.requests).containsExactly(
            StringsRequest(LanguageTag("fr", "CA"), TestParams),
            StringsRequest(LanguageTag("fr", ""), TestParams),
            StringsRequest(LanguageTag.DEFAULT, TestParams)
        ).inOrder()
        assertThat(result.await()).isEqualTo(
            TestStrings(
                title = "Exact title",
                message = "Language message",
                cancel = "Default cancel",
                shared = "Exact shared"
            )
        )
    }

    @Test
    fun resolvedStringsStartWithEmbeddedFallbacksAndUpdateWhenServerStringsArrive() = runTest {
        val languageTagsProvider = FakeLanguageTagsProvider(LanguageTag.DEFAULT)
        val serverRepository = FakeServerRepository()
        val provider = TestStringsProvider(languageTagsProvider, TestEmbeddedRepository, serverRepository, backgroundScope)
        val strings = provider.getStrings(TestParams)

        val initial = async { strings.filterNotNull().first() }
        runCurrent()
        assertThat(initial.await()).isEqualTo(TestEmbeddedRepository.fallback)

        val updated = async { strings.filterNotNull().drop(1).first() }
        runCurrent()
        serverRepository.flowFor(LanguageTag.DEFAULT).value = mapOf(
            "title" to "Server title",
            "message" to "Server message"
        )
        advanceTimeBy(50.milliseconds)
        runCurrent()

        assertThat(updated.await()).isEqualTo(
            TestStrings(
                title = "Server title",
                message = "Server message",
                cancel = TestEmbeddedRepository.fallback.cancel,
                shared = TestEmbeddedRepository.fallback.shared
            )
        )
    }

    @Test
    fun verificationRepositoriesKeepTemplatesAndFillMissingValues() {
        val emailStrings = EmailVerificationStringsEmbeddedRepositoryImpl().fallbackToEmbedded(
            EmailVerificationStringsParams(),
            mapOf(
                "title" to "Server email title",
                "message" to "Code %CODE_LENGTH% for %LOGIN_ID%"
            )
        )
        val phoneStrings = PhoneVerificationStringsEmbeddedRepositoryImpl().fallbackToEmbedded(
            PhoneVerificationStringsParams(),
            mapOf(
                "title" to "Server phone title",
                "message" to "SMS %CODE_LENGTH% for %LOGIN_ID%"
            )
        )

        assertThat(emailStrings.title).isEqualTo("Server email title")
        assertThat(emailStrings.message).isEqualTo("Code %CODE_LENGTH% for %LOGIN_ID%")
        assertThat(emailStrings.notYou).isEqualTo(EmailVerificationStrings.DEFAULT.notYou)

        assertThat(phoneStrings.title).isEqualTo("Server phone title")
        assertThat(phoneStrings.message).isEqualTo("SMS %CODE_LENGTH% for %LOGIN_ID%")
        assertThat(phoneStrings.notYou).isEqualTo(PhoneVerificationStrings.DEFAULT.notYou)
    }

    private object TestParams : StringsProvider.Params

    private data class TestStrings(
        val title: String,
        val message: String,
        val cancel: String,
        val shared: String
    ) : StringsProvider.Data

    private object TestEmbeddedRepository : StringsProvider.EmbeddedRepository<TestStrings> {
        val fallback = TestStrings(
            title = "Embedded title",
            message = "Embedded message",
            cancel = "Embedded cancel",
            shared = "Embedded shared"
        )

        override fun fallbackToEmbedded(params: StringsProvider.Params, map: Map<String, String>): TestStrings =
            TestStrings(
                title = map["title"] ?: fallback.title,
                message = map["message"] ?: fallback.message,
                cancel = map["cancel"] ?: fallback.cancel,
                shared = map["shared"] ?: fallback.shared
            )
    }

    private class TestStringsProvider(
        languageTagsProvider: LanguageTagsProvider,
        embeddedRepository: StringsProvider.EmbeddedRepository<TestStrings>,
        serverRepository: StringsProvider.ServerRepository,
        scope: kotlinx.coroutines.CoroutineScope
    ) : StringsProviderImpl<TestStrings, TestParams>(
        languageTagsProvider = languageTagsProvider,
        embeddedRepository = embeddedRepository,
        serverRepository = serverRepository,
        scope = scope,
        logger = null
    )

    private class FakeLanguageTagsProvider(initialLanguageTag: LanguageTag) : LanguageTagsProvider {
        private val tags = MutableStateFlow(listOf(initialLanguageTag))

        override fun setLanguageTags(tags: List<String>): Unit = error("Unexpected language update")

        override val languageTagsFlow: StateFlow<List<LanguageTag>>
            get() = tags
    }

    private class FakeServerRepository : StringsProvider.ServerRepository {
        private val flows = mutableMapOf<LanguageTag, MutableStateFlow<Map<String, String>?>>()
        val requests = mutableListOf<StringsRequest>()

        fun flowFor(languageTag: LanguageTag): MutableStateFlow<Map<String, String>?> =
            flows.getOrPut(languageTag) { MutableStateFlow(null) }

        override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> {
            requests += StringsRequest(languageTag, params)
            return flowFor(languageTag)
        }
    }

    private data class StringsRequest(
        val languageTag: LanguageTag,
        val params: StringsProvider.Params
    )
}
