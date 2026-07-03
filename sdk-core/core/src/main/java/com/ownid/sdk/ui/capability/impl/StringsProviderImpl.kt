package com.ownid.sdk.ui.capability.impl

import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.ui.capability.StringsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reactive strings provider that merges server locale overlays with embedded defaults.
 *
 * For each active primary language tag, server maps are combined in this order:
 * - the full primary tag;
 * - the language-only tag, when the primary tag has a country;
 * - [LanguageTag.DEFAULT], unless it is already covered.
 *
 * More specific maps win per key. `null` server emissions are treated as empty maps, so unavailable locale payloads and
 * suppressed refresh failures without usable content fall through to less-specific server maps and then to embedded
 * defaults. The embedded repository owns the final key-to-type mapping.
 *
 * The returned [StateFlow] starts with `null`, emits complete strings once a mapping is available, suppresses equal
 * consecutive values, and stays active while subscribed.
 */
@OptIn(InternalOwnIdAPI::class)
internal abstract class StringsProviderImpl<S : StringsProvider.Data, P : StringsProvider.Params>(
    private val languageTagsProvider: LanguageTagsProvider,
    private val embeddedRepository: StringsProvider.EmbeddedRepository<S>,
    private val serverRepository: StringsProvider.ServerRepository,
    private val scope: CoroutineScope,
    private val logger: OwnIdLogRouter?
) : StringsProvider<S, P> {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun getStrings(params: P): StateFlow<S?> =
        languageTagsProvider.languageTagsFlow
            .map { it.firstOrNull() ?: LanguageTag.DEFAULT }
            .flatMapLatest { tag ->
                val mergedServerFlow = buildList {
                    add(serverRepository.getStringsFlow(tag, params))
                    if (tag.country.isNotBlank()) {
                        add(serverRepository.getStringsFlow(tag.toLanguageOnly(), params))
                    }
                    if (tag != LanguageTag.DEFAULT && tag.toLanguageOnly() != LanguageTag.DEFAULT) {
                        add(serverRepository.getStringsFlow(LanguageTag.DEFAULT, params))
                    }
                }.fold(flowOf<Map<String, String>>(emptyMap())) { accFlow, nextFlow ->
                    combine(accFlow, nextFlow) { accValue, nextValue -> accValue + nextValue.orEmpty().filterKeys { it !in accValue } }
                }

                val mapped = mergedServerFlow
                    .map { serverStrings -> embeddedRepository.fallbackToEmbedded(params, serverStrings) }
                    .catch { e ->
                        logger?.logW(this, "getStrings", "Error getting strings: ${e.message}", e)
                        emit(embeddedRepository.fallbackToEmbedded(params, emptyMap()))
                    }

                var isFirst = true
                mapped.debounce { _ ->
                    if (isFirst) {
                        isFirst = false
                        0.milliseconds
                    } else 50.milliseconds
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5.seconds),
                initialValue = null
            )
}
