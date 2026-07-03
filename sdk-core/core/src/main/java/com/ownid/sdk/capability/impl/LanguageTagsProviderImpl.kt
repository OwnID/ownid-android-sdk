package com.ownid.sdk.capability.impl

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.ownid.sdk.capability.LanguageTagsProvider
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Root [LanguageTagsProvider] backed by application configuration callbacks.
 *
 * The provider publishes configuration languages while automatic tracking is active. A non-empty override unregisters
 * the callback until an empty override restores configuration tracking.
 */
@OptIn(InternalOwnIdAPI::class)
internal class LanguageTagsProviderImpl(
    private val context: Context,
    private val logger: OwnIdLogRouter?
) : LanguageTagsProvider, AutoCloseable {

    private val callbackLock = Any()

    private val _languageTagsFlow = MutableStateFlow(extractLanguageTags(context.resources.configuration))

    override val languageTagsFlow: StateFlow<List<LanguageTag>> = _languageTagsFlow.asStateFlow()

    private var componentCallback: ComponentCallbacks? = null

    init {
        startObservingConfigurationChanges()
        logger?.logD(this@LanguageTagsProviderImpl, "init", "Application languages: ${_languageTagsFlow.value}")
    }

    override fun close() {
        stopObservingConfigurationChanges()
    }

    override fun setLanguageTags(tags: List<String>) {
        if (tags.isEmpty()) {
            startObservingConfigurationChanges()
            _languageTagsFlow.value = extractLanguageTags(context.resources.configuration)
            logger?.logD(this@LanguageTagsProviderImpl, "setLanguageTags", "Languages: ${_languageTagsFlow.value}")
            return
        }

        stopObservingConfigurationChanges()

        _languageTagsFlow.value = tags
            .map { LanguageTag.fromLocale(Locale.forLanguageTag(it)) }
            .distinct()
            .ifEmpty { listOf(LanguageTag.DEFAULT) }

        logger?.logD(this@LanguageTagsProviderImpl, "setLanguageTags", "Languages: ${_languageTagsFlow.value}")
    }

    private fun startObservingConfigurationChanges() {
        synchronized(callbackLock) {
            if (componentCallback == null) {
                componentCallback = createComponentCallback().also { context.applicationContext.registerComponentCallbacks(it) }
            }
        }
    }

    private fun stopObservingConfigurationChanges() {
        synchronized(callbackLock) {
            componentCallback?.let { context.applicationContext.unregisterComponentCallbacks(it) }
            componentCallback = null
        }
    }

    private fun createComponentCallback(): ComponentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val updatedTags = extractLanguageTags(newConfig)
            if (_languageTagsFlow.value != updatedTags) {
                _languageTagsFlow.value = updatedTags
                logger?.logD(this@LanguageTagsProviderImpl, "onConfigurationChanged", "Languages: ${_languageTagsFlow.value}")
            }
        }

        @Deprecated("Deprecated in Android API.")
        override fun onLowMemory() = Unit
    }

    private fun extractLanguageTags(config: Configuration): List<LanguageTag> {
        val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.toLanguageTags()
                .split(",")
                .mapNotNull { tag ->
                    val trimmed = tag.trim()
                    if (trimmed.isEmpty()) null else Locale.forLanguageTag(trimmed)
                }
        } else {
            @Suppress("DEPRECATION")
            listOf(config.locale ?: Locale.getDefault())
        }

        return locales
            .map { LanguageTag.fromLocale(it) }
            .distinct()
            .ifEmpty { listOf(LanguageTag.DEFAULT) }
    }
}
