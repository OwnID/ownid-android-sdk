package com.ownid.sdk.ui.capability

import com.ownid.sdk.capability.Capability
import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides resolved strings for SDK-owned UI.
 *
 * Default SDK providers combine server locale maps with embedded fallback strings. For the primary [LanguageTag], the
 * server-backed provider reads the exact tag first, then its language-only tag when applicable, then [LanguageTag.DEFAULT].
 * Values from the more specific tag win. Missing server keys are filled by the embedded repository before the typed data
 * object is emitted.
 *
 * The returned flow updates when the primary language tag or upstream server locale data changes. Server-locale refresh
 * and caching are implementation details; callers observe only typed string data or the initial `null` value before the
 * first mapping is available.
 *
 * @param S Complete typed string data emitted by the provider.
 * @param P Parameters that select the operation-specific string variant.
 */
public interface StringsProvider<S : StringsProvider.Data, P : StringsProvider.Params> : Capability {

    /**
     * Marker interface for operation-specific parameters.
     */
    public interface Params

    /**
     * Marker interface for a complete operation-specific strings container.
     */
    public interface Data

    /**
     * Repository that converts server string keys into complete typed data.
     *
     * Implementations fill every missing key from embedded defaults, including the case where the server map is empty.
     */
    public interface EmbeddedRepository<S> : Capability {
        /**
         * Returns complete strings by applying embedded defaults to [map].
         */
        public fun fallbackToEmbedded(params: Params, map: Map<String, String>): S
    }

    /**
     * Repository that exposes raw string keys for one server locale payload.
     */
    public interface ServerRepository : Capability {
        /**
         * Produces raw localized strings for [languageTag], or `null` when no server locale payload is available.
         */
        public fun getStringsFlow(languageTag: LanguageTag, params: Params): Flow<Map<String, String>?>
    }

    /**
     * Returns a flow of resolved strings for the given [params].
     *
     * The returned [StateFlow] starts with `null` for server-backed providers, then emits complete strings after the
     * first mapping. Embedded-only providers may emit complete strings immediately.
     */
    public fun getStrings(params: P): StateFlow<S?>
}
