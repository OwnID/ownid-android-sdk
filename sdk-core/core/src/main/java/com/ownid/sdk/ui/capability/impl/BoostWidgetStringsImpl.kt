package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.ui.capability.BoostWidgetStrings
import com.ownid.sdk.ui.capability.BoostWidgetStringsProvider
import com.ownid.sdk.ui.capability.StringsProvider
import com.ownid.sdk.ui.locale.ServerLocaleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps Boost widget server keys into typed strings and fills missing keys from embedded defaults.
 */
internal class BoostWidgetStringsEmbeddedRepositoryImpl : BoostWidgetStringsProvider.EmbeddedRepository {

    override fun fallbackToEmbedded(params: StringsProvider.Params, map: Map<String, String>): BoostWidgetStrings =
        BoostWidgetStrings(
            skipPassword = map["skipPassword"] ?: BoostWidgetStrings.DEFAULT.skipPassword,
            or = map["or"] ?: BoostWidgetStrings.DEFAULT.or,
        )
}

/**
 * Reads Boost widget strings from the server locale data source.
 *
 * Missing locale data emits `null`; missing individual keys are omitted so the embedded repository can fill them.
 * Unavailable or unreadable persisted locale payloads are visible here as missing server data; suppressed refresh
 * failures may continue serving previously available data when a payload exists.
 */
internal class BoostWidgetStringsServerRepositoryImpl(
    private val serverLocaleProvider: ServerLocaleDataSource.Provider
) : BoostWidgetStringsProvider.ServerRepository {

    private val prefix = arrayOf("widgets")

    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> {
        return serverLocaleProvider.getDataSource(languageTag).map { dataSource ->
            if (dataSource == null) return@map null

            mapOf(
                "skipPassword" to dataSource.getString(*prefix, "sbs-button", "skipPassword"),
                "or" to dataSource.getString(*prefix, "sbs-button", "or"),
            ).filterValues { it != null }.mapValues { it.value!! }
        }
    }
}

/**
 * Server repository used when locale data source setup is unavailable.
 *
 * Emits an empty map so string resolution completes with embedded defaults.
 */
internal object BoostWidgetStringsServerRepositoryEmptyFallback : BoostWidgetStringsProvider.ServerRepository {
    override fun getStringsFlow(languageTag: LanguageTag, params: StringsProvider.Params): Flow<Map<String, String>?> =
        flowOf(emptyMap())
}
