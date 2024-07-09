package com.ownid.sdk.internal.component.repository

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.OwnIdLoginId
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

    @JvmSynthetic
    internal suspend fun getLoginId(): OwnIdLoginId =
        storage.getString(LOGIN_ID_PREF_KEY)?.let { OwnIdLoginId(it) } ?: OwnIdLoginId.EMPTY

    @JvmSynthetic
    @Throws(IOException::class)
    internal suspend fun saveLoginId(loginId: OwnIdLoginId) = withContext(NonCancellable) {
        storage.saveString(LOGIN_ID_PREF_KEY, loginId.value)
    }

    private val OwnIdLoginId.dataPreferencesKey: Preferences.Key<String>
        get() {
            val suffix = this.value.encodeToByteArray().toSHA256Bytes().toBase64UrlSafeNoPadding()
            return stringPreferencesKey("com.ownid.sdk.storage.KEY_LOGIN_ID_DATA_$suffix")
        }

    @JvmSynthetic
    internal suspend fun getLoginIdData(loginId: OwnIdLoginId): OwnIdLoginId.Data =
        storage.getString(loginId.dataPreferencesKey)
            ?.let { json -> OwnIdLoginId.Data.fromJsonString(json) }
            ?: OwnIdLoginId.Data()

    @JvmSynthetic
    @Throws(IOException::class)
    internal suspend fun saveLoginIdData(loginId: OwnIdLoginId, loginIdData: OwnIdLoginId.Data) = withContext(NonCancellable) {
        storage.saveString(loginId.dataPreferencesKey, loginIdData.toJsonString())
    }
}