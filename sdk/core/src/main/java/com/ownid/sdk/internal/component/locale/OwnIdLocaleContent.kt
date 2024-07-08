package com.ownid.sdk.internal.component.locale

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONException
import org.json.JSONObject

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdLocaleContent(
    internal val ownIdLocale: OwnIdLocale,
    private val content: JSONObject,
    private val timeStamp: Long = System.currentTimeMillis()
) {

    internal companion object {
        private const val ANDROID_SUFFIX: String = "-android"
        private const val LOCALE_CACHE_TIME: Long = 1000L * 60L * 10L // 10 Minutes

        internal fun fromCache(ownIdLocale: OwnIdLocale, cache: DiskLruCache): OwnIdLocaleContent? =
            CachedString.get(ownIdLocale.cacheKey(), cache)?.run { OwnIdLocaleContent(ownIdLocale, JSONObject(data), timeStamp) }
    }

    internal fun saveToCache(cache: DiskLruCache) = CachedString(timeStamp, content.toString()).put(ownIdLocale.cacheKey(), cache)

    internal fun hasString(ownIdLocaleKey: OwnIdLocaleKey): Boolean {
        if (ownIdLocaleKey.keys.isEmpty()) return false

        val valueKey = ownIdLocaleKey.keys.last()
        val valueKeyAndroid = valueKey + ANDROID_SUFFIX

        if (ownIdLocaleKey.keys.size == 1) return content.has(valueKeyAndroid) || content.has(valueKey)

        repeat(ownIdLocaleKey.keys.size - 1) { counter ->
            runCatching { getJsonObjectForPathOrThrow(ownIdLocaleKey.keys.take(ownIdLocaleKey.keys.size - (counter + 1))) }
                .onSuccess { json -> if (json.has(valueKeyAndroid) || json.has(valueKey)) return true } // if false try shorter key
        }

        return false
    }

    @Throws(JSONException::class)
    internal fun getString(ownIdLocaleKey: OwnIdLocaleKey): String {
        val valueKey = ownIdLocaleKey.keys.last()
        val valueKeyAndroid = valueKey + ANDROID_SUFFIX

        if (ownIdLocaleKey.keys.size == 1) {
            return if (content.has(valueKeyAndroid)) content.getString(valueKeyAndroid) else content.getString(valueKey)
        }

        repeat(ownIdLocaleKey.keys.size - 1) { counter ->
            runCatching { getJsonObjectForPathOrThrow(ownIdLocaleKey.keys.take(ownIdLocaleKey.keys.size - (counter + 1))) }
                .onSuccess { json ->
                    if (json.has(valueKeyAndroid)) return json.getString(valueKeyAndroid)
                    if (json.has(valueKey)) return json.getString(valueKey)
                } // if false try shorter key
        }

        throw JSONException("No value found")
    }

    internal fun isExpired(): Boolean = System.currentTimeMillis() - timeStamp > LOCALE_CACHE_TIME

    @Throws(JSONException::class)
    private fun getJsonObjectForPathOrThrow(keys: List<String>): JSONObject = keys.fold(content) { json, key -> json.getJSONObject(key) }
}