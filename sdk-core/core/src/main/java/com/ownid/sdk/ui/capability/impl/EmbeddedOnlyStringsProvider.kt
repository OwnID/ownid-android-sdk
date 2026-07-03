package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.ui.capability.StringsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [StringsProvider] that only uses embedded defaults without any server data.
 *
 * Used when locale-backed provider setup fails. The returned [StateFlow] is created with the mapped embedded strings
 * and does not react to language changes, server refreshes, or stored-locale changes.
 */
internal open class EmbeddedOnlyStringsProvider<S : StringsProvider.Data, P : StringsProvider.Params>(
    private val embeddedRepository: StringsProvider.EmbeddedRepository<S>,
) : StringsProvider<S, P> {

    override fun getStrings(params: P): StateFlow<S?> {
        val strings = embeddedRepository.fallbackToEmbedded(params, emptyMap())
        return MutableStateFlow(strings)
    }
}
