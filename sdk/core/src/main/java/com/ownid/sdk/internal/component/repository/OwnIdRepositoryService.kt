package com.ownid.sdk.internal.component.repository

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.AuthMethod
import com.ownid.sdk.internal.OwnIdLoginIdData
import com.ownid.sdk.internal.toBase64UrlSafeNoPadding
import com.ownid.sdk.internal.toSHA256Bytes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdRepositoryService private constructor(private val storage: OwnIdStorage) {

    internal companion object {
        private val LOGIN_ID_PREF_KEY = stringPreferencesKey("com.ownid.sdk.storage.KEY_LOGIN_ID")

        internal fun create(context: Context, appId: String): OwnIdRepositoryService =
            OwnIdRepositoryService(OwnIdStorage(context, appId))
    }

    internal suspend fun getLoginId(): String? = storage.getString(LOGIN_ID_PREF_KEY)

    @Throws(IOException::class)
    internal suspend fun saveLoginId(loginId: String, authMethod: AuthMethod?) = withContext(NonCancellable) {
        storage.saveString(LOGIN_ID_PREF_KEY, loginId)
        saveLoginIdData(loginId, getLoginIdData(loginId).copy(authMethod = authMethod))
    }

    private val String.dataPreferencesKey: Preferences.Key<String>
        get() {
            val suffix = encodeToByteArray().toSHA256Bytes().toBase64UrlSafeNoPadding()
            return stringPreferencesKey("com.ownid.sdk.storage.KEY_LOGIN_ID_DATA_$suffix")
        }

    internal suspend fun getLoginIdData(loginId: String): OwnIdLoginIdData =
        storage.getString(loginId.dataPreferencesKey)
            ?.let { json -> OwnIdLoginIdData.fromJsonString(json) }
            ?: OwnIdLoginIdData()

    @Throws(IOException::class)
    internal suspend fun saveLoginIdData(loginId: String, loginIdOwnIdLoginIdData: OwnIdLoginIdData) =
        withContext(NonCancellable) {
            storage.saveString(loginId.dataPreferencesKey, loginIdOwnIdLoginIdData.toJsonString())
        }
}