package com.ownid.sdk.internal

import android.content.Context
import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdStorageService(context: Context, appId: String) {

    @InternalOwnIdAPI
    internal companion object {
        private const val KEY_LOGIN_ID: String = "com.ownid.sdk.internal.storage.KEY_LOGIN_ID"
    }

    init {
        OwnIdInternalLogger.logI(this, "init", "Invoked")
    }

    private val preferences = context.getSharedPreferences("com.ownid.sdk.internal.core_data_$appId", Context.MODE_PRIVATE)

    @JvmSynthetic
    internal fun getLastLoginId(): String =
        runCatching { preferences.getString(KEY_LOGIN_ID, "") ?: "" }.getOrDefault("")

    @JvmSynthetic
    internal fun saveLoginId(loginId: String) =
        preferences.edit().apply { putString(KEY_LOGIN_ID, loginId) }.apply()
}