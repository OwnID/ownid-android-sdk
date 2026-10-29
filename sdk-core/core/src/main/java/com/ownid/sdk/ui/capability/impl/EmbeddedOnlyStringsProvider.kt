package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.ui.capability.StringsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [StringsProvider] that only uses embedded defaults without any server data.
 *
 * Used when locale-backed provider setup fails. Each collection emits the mapped embedded strings and completes.
 */
internal open class EmbeddedOnlyStringsProvider<S : StringsProvider.Data, P : StringsProvider.Params>(
    private val embeddedRepository: StringsProvider.EmbeddedRepository<S>,
) : StringsProvider<S, P> {

    override fun getStrings(params: P): Flow<S?> = flow {
        emit(embeddedRepository.fallbackToEmbedded(params, emptyMap()))
    }
}
