package com.ownid.sdk.ui.locale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ownid.sdk.capability.Serialization
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistence owner for server-provided locale payloads.
 *
 * Content is scoped to the SDK app/environment identity. Callers read and write [ServerLocaleContent] values by
 * [LanguageTag]; storage details are intentionally not part of the locale repository contract.
 *
 * Corrupted or unreadable data is treated as unavailable content so string resolution can fall back to embedded copy.
 * Write failures are allowed to surface to the refresh path; readers should continue to tolerate missing content.
 */
@InternalOwnIdAPI
internal class ServerLocaleDataStore(
    context: Context,
    scope: CoroutineScope,
    private val suffix: String,
    private val serialization: Serialization,
    private val logger: OwnIdLogRouter?
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler {
            logger?.logW(this, "ReplaceFileCorruptionHandler", "DataStore corrupted: $it", it)
            emptyPreferences()
        },
        scope = scope,
        produceFile = { File(context.cacheDir, "ownid/locales/$suffix.preferences_pb") }
    )

    @Throws
    internal fun getDataSource(languageTag: LanguageTag): Flow<ServerLocaleContent?> = dataStore.data
        .catch { _ -> emit(emptyPreferences()) }
        .map { preferences ->
            runCatchingExceptions {
                val rawJson = preferences[languageTag.preferenceKey] ?: return@map null
                serialization.decodeFromString(ServerLocaleContent.serializer(), rawJson)
            }.getOrNull()
        }

    @Throws
    internal suspend fun getContent(languageTag: LanguageTag): ServerLocaleContent? =
        dataStore.data.firstOrNull()?.get(languageTag.preferenceKey)?.let { rawJson ->
            serialization.decodeFromString(ServerLocaleContent.serializer(), rawJson)
        }

    @Throws
    internal suspend fun setContent(languageTag: LanguageTag, content: ServerLocaleContent) {
        val encoded = serialization.encodeToString(ServerLocaleContent.serializer(), content)
        withContext(NonCancellable) {
            dataStore.edit { prefs -> prefs[languageTag.preferenceKey] = encoded }
        }
    }

    private val LanguageTag.preferenceKey
        get() = stringPreferencesKey(tagString)
}
