package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.ui.capability.PhoneVerificationStrings
import com.ownid.sdk.ui.capability.PhoneVerificationStringsProvider
import com.ownid.sdk.ui.capability.StringsProvider
import com.ownid.sdk.ui.locale.ServerLocaleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps phone verification server keys into typed strings and fills missing keys from embedded defaults.
 */
internal class PhoneVerificationStringsEmbeddedRepositoryImpl : PhoneVerificationStringsProvider.EmbeddedRepository {

    override fun fallbackToEmbedded(params: StringsProvider.Params, map: Map<String, String>): PhoneVerificationStrings =
        PhoneVerificationStrings(
            title = map["title"] ?: PhoneVerificationStrings.DEFAULT.title,
            message = map["message"] ?: PhoneVerificationStrings.DEFAULT.message,
            description = map["description"] ?: PhoneVerificationStrings.DEFAULT.description,
            resend = map["resend"] ?: PhoneVerificationStrings.DEFAULT.resend,
            cancel = map["cancel"] ?: PhoneVerificationStrings.DEFAULT.cancel,
            notYou = map["notYou"] ?: PhoneVerificationStrings.DEFAULT.notYou,
        )
}

/**
 * Reads phone verification strings from the server locale data source.
 *
 * Missing locale data emits `null`; missing individual keys are omitted so the embedded repository can fill them.
 * Unavailable or unreadable persisted locale payloads are visible here as missing server data; suppressed refresh
 * failures may continue serving previously available data when a payload exists.
 */
internal class PhoneVerificationStringsServerRepositoryImpl(
    private val serverLocaleProvider: ServerLocaleDataSource.Provider
) : PhoneVerificationStringsProvider.ServerRepository {

    private val prefix = arrayOf("steps", "otp")

    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> {

        return serverLocaleProvider.getDataSource(languageTag).map { dataSource ->
            if (dataSource == null) return@map null

            mapOf(
                "title" to dataSource.getString(*prefix, "verify", "sms", "title"),
                "message" to dataSource.getString(*prefix, "sms", "verify", "message"),
                "description" to dataSource.getString(*prefix, "sms", "verify", "description"),
                "resend" to dataSource.getString(*prefix, "sms", "verify", "resend"),
                "cancel" to dataSource.getString(*prefix, "sms", "verify", "cancel"),
                "notYou" to dataSource.getString(*prefix, "sms", "verify", "not-you")
            ).filterValues { it != null }.mapValues { it.value!! }
        }
    }
}

/**
 * Server repository used when locale data source setup is unavailable.
 *
 * Emits an empty map so string resolution completes with embedded defaults.
 */
internal object PhoneVerificationStringsServerRepositoryEmptyFallback : PhoneVerificationStringsProvider.ServerRepository {
    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> =
        flowOf(emptyMap())
}
