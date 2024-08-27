package com.ownid.sdk.internal.component.locale

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdServerLocales(languageTags: List<String>, private val timeStamp: Long = System.currentTimeMillis()) {

    private val ownIdLocales: List<OwnIdLocale> = languageTags.map { OwnIdLocale.forLanguageTag(it) }

    internal companion object {
        private const val CACHE_KEY: String = "locales"

        internal fun fromCache(cache: DiskLruCache): OwnIdServerLocales = runCatching {
            val (timeStamp, data) = CachedString.get(CACHE_KEY, cache) ?: return@runCatching OwnIdServerLocales(emptyList<String>(), 0)
            val languageTagArray = JSONObject(data).getJSONArray(CACHE_KEY)
            val languageTags = List(languageTagArray.length()) { languageTagArray.getString(it) }
            OwnIdServerLocales(languageTags, timeStamp)
        }.getOrNull() ?: OwnIdServerLocales(emptyList(), 0)
    }

    internal fun saveToCache(cache: DiskLruCache) {
        val data = JSONObject().put(CACHE_KEY, JSONArray(ownIdLocales.map { it.serverLanguageTag })).toString()
        CachedString(timeStamp, data).put(CACHE_KEY, cache)
    }

    internal fun size(): Int = ownIdLocales.size

    internal fun containsLocale(ownIdLocale: OwnIdLocale): Boolean =
        ownIdLocales.any { it.serverLanguageTag == ownIdLocale.serverLanguageTag }

    internal fun selectLocale(languageTags: String): OwnIdLocale = languageTags.split(",").firstNotNullOfOrNull { tag ->
        val locale = Locale.forLanguageTag(tag)
        run {
            val languageAndCountry = Locale(locale.language, locale.country)
            ownIdLocales.firstOrNull { it.isSameLocale(languageAndCountry) }
        } ?: run {
            val language = Locale(locale.language)
            ownIdLocales.firstOrNull { it.isSameLocale(language) }
        }
    } ?: OwnIdLocale.DEFAULT
}