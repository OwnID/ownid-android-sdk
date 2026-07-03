package com.ownid.sdk.capability.impl

import com.google.common.truth.Truth.assertThat
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Network
import com.ownid.sdk.logger.OwnIdLogger
import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

// Covers: NET-TRANSPORT-060
internal class DefaultOkHttpClientFactoryContractTest {

    @Test
    fun metadataInterceptorAddsMissingSdkMetadataHeaders() {
        val request = capturedRequest(
            localInfo = localInfo(),
            languageTagsProvider = languageTagsProvider(LanguageTag("en", "US"), LanguageTag("fr", "")),
            appUrlHeaderValue = "app123.server.ownid.com"
        )

        assertThat(request.header(Network.Request.Header.USER_AGENT)).isEqualTo("OwnID-Test/1.0")
        assertThat(request.header(Network.Request.Header.ACCEPT_LANGUAGE)).isEqualTo("en-US,fr")
        assertThat(request.header(Network.Request.Header.OWNID_APP_URL)).isEqualTo("app123.server.ownid.com")
        assertThat(request.header(Network.Request.Header.BAGGAGE)).isEqualTo("sdk.correlation_id=correlation-1")
    }

    @Test
    fun metadataInterceptorPreservesExplicitHeadersAndMergesMissingCorrelationBaggage() {
        val request = capturedRequest(
            localInfo = localInfo(),
            languageTagsProvider = languageTagsProvider(LanguageTag("en", "US")),
            appUrlHeaderValue = "app123.server.ownid.com",
            request = Request.Builder()
                .url("https://api.example.test/path")
                .header(Network.Request.Header.USER_AGENT, "Host-App/2.0")
                .header(Network.Request.Header.ACCEPT_LANGUAGE, "es-MX")
                .header(Network.Request.Header.OWNID_APP_URL, "custom.server.ownid.com")
                .header(Network.Request.Header.BAGGAGE, "tenant=example")
                .build()
        )

        assertThat(request.header(Network.Request.Header.USER_AGENT)).isEqualTo("Host-App/2.0")
        assertThat(request.header(Network.Request.Header.ACCEPT_LANGUAGE)).isEqualTo("es-MX")
        assertThat(request.header(Network.Request.Header.OWNID_APP_URL)).isEqualTo("custom.server.ownid.com")
        assertThat(request.header(Network.Request.Header.BAGGAGE)).isEqualTo("tenant=example,sdk.correlation_id=correlation-1")
    }

    @Test
    fun metadataInterceptorDoesNotDuplicateExistingCorrelationBaggage() {
        val request = capturedRequest(
            localInfo = localInfo(),
            languageTagsProvider = languageTagsProvider(LanguageTag("en", "US")),
            appUrlHeaderValue = "app123.server.ownid.com",
            request = Request.Builder()
                .url("https://api.example.test/path")
                .header(Network.Request.Header.BAGGAGE, "tenant=example,sdk.correlation_id=existing")
                .build()
        )

        assertThat(request.header(Network.Request.Header.BAGGAGE)).isEqualTo("tenant=example,sdk.correlation_id=existing")
    }

    @Test
    fun metadataInterceptorFallsBackToEnglishWhenLanguageListIsEmpty() {
        val request = capturedRequest(
            localInfo = null,
            languageTagsProvider = languageTagsProvider(),
            appUrlHeaderValue = "app123.server.ownid.com"
        )

        assertThat(request.header(Network.Request.Header.ACCEPT_LANGUAGE)).isEqualTo("en")
        assertThat(request.header(Network.Request.Header.OWNID_APP_URL)).isEqualTo("app123.server.ownid.com")
        assertThat(request.header(Network.Request.Header.USER_AGENT)).isNull()
        assertThat(request.header(Network.Request.Header.BAGGAGE)).isNull()
    }

    private fun capturedRequest(
        localInfo: LocalInfo?,
        languageTagsProvider: LanguageTagsProvider?,
        appUrlHeaderValue: String,
        ownIdLogger: OwnIdLogger? = null,
        request: Request = Request.Builder().url("https://api.example.test/path").build()
    ): Request {
        val capturedRequests = mutableListOf<Request>()
        val client = DefaultOkHttpClientFactory.create(
            localInfo = localInfo,
            languageTagsProvider = languageTagsProvider,
            appUrlHeaderValue = appUrlHeaderValue,
            ownIdLogger = ownIdLogger
        ).newBuilder()
            .addInterceptor { chain ->
                val capturedRequest = chain.request()
                capturedRequests += capturedRequest
                Response.Builder()
                    .request(capturedRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        client.newCall(request).execute().close()

        return capturedRequests.single()
    }

    private fun localInfo(): LocalInfo = object : LocalInfo {
        override val modules: List<Pair<String, String>> = emptyList()
        override val packageName: String = "com.example.app"
        override val appVersion: String = "1.0"
        override val userAgent: String = "OwnID-Test/1.0"
        override val correlationId: String = "correlation-1"
        override val isDebuggable: Boolean = false
        override val isSystemFidoCapable: Boolean = true
        override val isDeviceSecured: Boolean = true
        override val isFingerprintHardwarePresent: Boolean? = null
        override val isFaceHardwarePresent: Boolean? = null
        override val isIrisHardwarePresent: Boolean? = null
        override val isStrongBiometricEnabled: Boolean? = null
    }

    private fun languageTagsProvider(vararg tags: LanguageTag): LanguageTagsProvider = object : LanguageTagsProvider {
        override val languageTagsFlow: StateFlow<List<LanguageTag>> = MutableStateFlow(tags.toList())

        override fun setLanguageTags(tags: List<String>) = Unit
    }
}
