package com.ownid.sdk.ui.locale

import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of server locale content for one language tag.
 *
 * Provides key-path lookup into cached JSON content. Missing keys, missing paths, non-string values, and placeholder
 * entries are exposed as `null` so callers can continue to embedded string fallbacks.
 */
internal interface ServerLocaleDataSource {

    /** Supplies [ServerLocaleDataSource] values as a reactive stream per language tag. */
    interface Provider {
        /**
         * Returns a flow of locale data sources for [languageTag].
         *
         * The flow emits `null` when no locale JSON content is currently available for the tag, including a missing
         * server locale, unreadable persisted value, or suppressed refresh failure without payload.
         */
        fun getDataSource(languageTag: LanguageTag): Flow<ServerLocaleDataSource?>
    }

    /** The language tag this data source was loaded for. */
    val languageTag: LanguageTag

    /**
     * Resolves a localized string by walking the JSON tree along [key].
     *
     * Lookup behavior:
     * - Resolves parent objects along the path.
     * - At each resolved level (from deepest to root), prefers `<valueKey>-android` over `<valueKey>`.
     * - Returns the first match found; otherwise returns `null`.
     */
    fun getString(vararg key: String): String?
}
