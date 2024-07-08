package com.ownid.sdk.internal.component.repository

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.component.OwnIdInternalLogger
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdStorage(context: Context, appId: String) {

    init {
        OwnIdInternalLogger.logD(this, "init", "Invoked")
    }

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { error ->
            OwnIdInternalLogger.logW(this@OwnIdStorage, "PreferenceDataStoreFactory.create", error.message, error)
            emptyPreferences()
        },
        produceFile = {
            context.preferencesDataStoreFile("com.ownid.sdk.storage.core_$appId")
        }
    )

    @JvmSynthetic
    @Throws(IOException::class)
    internal suspend fun saveString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { preferences -> preferences[key] = value }
    }

    @JvmSynthetic
    internal suspend fun getString(key: Preferences.Key<String>): String? {
        return dataStore.data
            .catch { error ->
                OwnIdInternalLogger.logW(this@OwnIdStorage, "OwnIdStorage.getString.data", error.message, error)
                emit(emptyPreferences())
            }
            .map { preferences -> preferences[key] }
            .catch { error ->
                OwnIdInternalLogger.logW(this@OwnIdStorage, "OwnIdStorage.getString.get", error.message, error)
                emit(null)
            }
            .first()
    }
}