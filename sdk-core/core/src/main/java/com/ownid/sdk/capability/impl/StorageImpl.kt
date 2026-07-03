package com.ownid.sdk.capability.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ownid.sdk.capability.Storage
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Default [Storage] for a single SDK storage name.
 *
 * The instance persists values in SDK-owned storage associated with the configured storage name. Maintain one live
 * instance per storage name so writes observe a single owner. Corrupted storage is logged and replaced with an empty
 * store. Read [IOException]s return the supplied default, while write and remove [IOException]s are logged and
 * rethrown.
 *
 * !!! MUST BE SINGLETON PER FILE NAME !!!
 */
@OptIn(InternalOwnIdAPI::class)
internal class StorageImpl(
    context: Context,
    fileName: String,
    scope: CoroutineScope,
    private val prefix: String = "com.ownid.sdk.storage.",
    private val logger: OwnIdLogRouter?
) : Storage {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler {
            logger?.logW(this, "ReplaceFileCorruptionHandler", "DataStore corrupted: $it", it)
            emptyPreferences()
        },
        scope = scope,
        produceFile = { File(context.noBackupFilesDir, "ownid/$fileName.preferences_pb") }
    )

    override suspend fun getString(key: String, default: String?): String? = getValue(stringKey(key), default)
    override suspend fun putString(key: String, value: String) = putValue(stringKey(key), value)

    override suspend fun getBoolean(key: String, default: Boolean?): Boolean? = getValue(booleanKey(key), default)
    override suspend fun putBoolean(key: String, value: Boolean) = putValue(booleanKey(key), value)

    override suspend fun getNumber(key: String, default: Long?): Long? = getValue(longKey(key), default)
    override suspend fun putNumber(key: String, value: Long) = putValue(longKey(key), value)

    override suspend fun getDouble(key: String, default: Double?): Double? = getValue(doubleKey(key), default)
    override suspend fun putDouble(key: String, value: Double) = putValue(doubleKey(key), value)

    override suspend fun remove(key: String) = removeValue(stringKey(key))

    private fun stringKey(key: String) = stringPreferencesKey("$prefix$key")
    private fun booleanKey(key: String) = booleanPreferencesKey("$prefix$key")
    private fun longKey(key: String) = longPreferencesKey("$prefix$key")
    private fun doubleKey(key: String) = doublePreferencesKey("$prefix$key")

    private suspend fun <T> putValue(key: Preferences.Key<T>, value: T): Unit = withContext(NonCancellable) {
        try {
            dataStore.edit { prefs -> prefs[key] = value }
        } catch (e: IOException) {
            logger?.logW(this@StorageImpl, "putValue", "DataStore write failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun <T> getValue(key: Preferences.Key<T>, default: T? = null): T? = try {
        dataStore.data.first()[key] ?: default
    } catch (e: IOException) {
        logger?.logW(this@StorageImpl, "getValue", "DataStore read failed: ${e.message}", e)
        default
    }

    private suspend fun <T> removeValue(key: Preferences.Key<T>): Unit = withContext(NonCancellable) {
        try {
            dataStore.edit { prefs -> prefs.remove(key) }
        } catch (e: IOException) {
            logger?.logW(this@StorageImpl, "removeValue", "DataStore write failed: ${e.message}", e)
            throw e
        }
    }
}
