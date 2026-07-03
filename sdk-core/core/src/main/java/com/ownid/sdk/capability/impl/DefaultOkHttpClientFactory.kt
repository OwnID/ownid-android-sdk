package com.ownid.sdk.capability.impl

import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.capability.LocalInfo
import com.ownid.sdk.capability.Network
import com.ownid.sdk.logger.HttpLoggingInterceptor
import com.ownid.sdk.logger.OwnIdLogger
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object DefaultOkHttpClientFactory {
    fun create(
        localInfo: LocalInfo?,
        languageTagsProvider: LanguageTagsProvider?,
        appUrlHeaderValue: String,
        ownIdLogger: OwnIdLogger?
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
            .addInterceptor { chain ->
                val original = chain.request()
                var modified = false
                val builder = original.newBuilder()

                if (original.header(Network.Request.Header.USER_AGENT) == null && localInfo != null) {
                    builder.header(Network.Request.Header.USER_AGENT, localInfo.userAgent)
                    modified = true
                }

                if (original.header(Network.Request.Header.ACCEPT_LANGUAGE) == null && languageTagsProvider != null) {
                    val languages = languageTagsProvider.languageTagsFlow.value
                    val acceptLanguage = languages.joinToString(",") { it.toString() }.ifBlank { "en" }
                    builder.header(Network.Request.Header.ACCEPT_LANGUAGE, acceptLanguage)
                    modified = true
                }

                if (original.header(Network.Request.Header.OWNID_APP_URL) == null) {
                    builder.header(Network.Request.Header.OWNID_APP_URL, appUrlHeaderValue)
                    modified = true
                }

                if (localInfo != null) {
                    val baggagePair = "sdk.correlation_id=${localInfo.correlationId}"
                    val existingValues = original.headers(Network.Request.Header.BAGGAGE)
                    val existingCombined = existingValues.joinToString(",")
                    val hasExistingCorrelation = existingCombined.contains("sdk.correlation_id=", ignoreCase = true)
                    val merged = when {
                        existingCombined.isBlank() -> baggagePair
                        hasExistingCorrelation -> existingCombined
                        else -> "$existingCombined,$baggagePair"
                    }
                    builder.header(Network.Request.Header.BAGGAGE, merged)
                    modified = true
                }

                val next = if (modified) builder.build() else original
                chain.proceed(next)
            }
            .apply {
                // Http logging only logged locally, no logs send to server.
                // Http logging only works with manual log level setup before OwnId.initialize {...}, like:
                // OwnId.logger {
                //      level = OwnIdLogger.Level.Verbose or Debug
                // }
                // Any server side logs level will not trigger Http logs as OkHttpClient is already created (unless DI was reset).
                if (ownIdLogger != null && ownIdLogger.isEnabled(OwnIdLogger.Level.Debug)) {
                    val interceptor = HttpLoggingInterceptor(ownIdLogger.level) { message ->
                        ownIdLogger.log(ownIdLogger.level, "OwnIdApi", message)
                    }
                    addInterceptor(interceptor) // Must be last interceptor
                }
            }
            .build()
    }
}
