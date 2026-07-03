package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.ui.capability.EmailVerificationStrings
import com.ownid.sdk.ui.capability.EmailVerificationStringsProvider
import com.ownid.sdk.ui.capability.StringsProvider
import com.ownid.sdk.ui.locale.ServerLocaleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps email verification server keys into typed strings and fills missing keys from embedded defaults.
 */
internal class EmailVerificationStringsEmbeddedRepositoryImpl : EmailVerificationStringsProvider.EmbeddedRepository {

    override fun fallbackToEmbedded(params: StringsProvider.Params, map: Map<String, String>): EmailVerificationStrings =
        EmailVerificationStrings(
            title = map["title"] ?: EmailVerificationStrings.DEFAULT.title,
            message = map["message"] ?: EmailVerificationStrings.DEFAULT.message,
            description = map["description"] ?: EmailVerificationStrings.DEFAULT.description,
            resend = map["resend"] ?: EmailVerificationStrings.DEFAULT.resend,
            cancel = map["cancel"] ?: EmailVerificationStrings.DEFAULT.cancel,
            notYou = map["notYou"] ?: EmailVerificationStrings.DEFAULT.notYou,
        )
}

/**
 * Reads email verification strings from the server locale data source.
 *
 * Missing locale data emits `null`; missing individual keys are omitted so the embedded repository can fill them.
 * Unavailable or unreadable persisted locale payloads are visible here as missing server data; suppressed refresh
 * failures may continue serving previously available data when a payload exists.
 */
internal class EmailVerificationStringsServerRepositoryImpl(
    private val serverLocaleProvider: ServerLocaleDataSource.Provider
) : EmailVerificationStringsProvider.ServerRepository {

    private val prefix = arrayOf("steps", "otp")

    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> {
        return serverLocaleProvider.getDataSource(languageTag).map { dataSource ->
            if (dataSource == null) return@map null

            mapOf(
                "title" to dataSource.getString(*prefix, "verify", "email", "title"),
                "message" to dataSource.getString(*prefix, "email", "verify", "message"),
                "description" to dataSource.getString(*prefix, "email", "verify", "description"),
                "resend" to dataSource.getString(*prefix, "email", "verify", "resend"),
                "cancel" to dataSource.getString(*prefix, "email", "verify", "cancel"),
                "notYou" to dataSource.getString(*prefix, "email", "verify", "not-you")
            ).filterValues { it != null }.mapValues { it.value!! }
        }
    }
}

/**
 * Server repository used when locale data source setup is unavailable.
 *
 * Emits an empty map so string resolution completes with embedded defaults.
 */
internal object EmailVerificationStringsServerRepositoryEmptyFallback : EmailVerificationStringsProvider.ServerRepository {
    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> =
        flowOf(emptyMap())
}
