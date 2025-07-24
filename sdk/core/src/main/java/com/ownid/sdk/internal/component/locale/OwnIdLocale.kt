package com.ownid.sdk.internal.component.locale

import androidx.annotation.RestrictTo
import com.ownid.sdk.InternalOwnIdAPI
import java.util.Locale

@InternalOwnIdAPI
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class OwnIdLocale(internal val serverLanguageTag: String, internal val locale: Locale) {

    internal companion object {
        internal val DEFAULT = OwnIdLocale(Locale.ENGLISH.language, Locale.ENGLISH)
        internal fun forLanguageTag(languageTag: String): OwnIdLocale = OwnIdLocale(languageTag, Locale.forLanguageTag(languageTag))
    }

    internal fun cacheKey(): String = serverLanguageTag.lowercase(Locale.ROOT)

    internal fun isSameLocale(locale: Locale): Boolean = this.locale.equals(locale)

    override fun toString(): String = "OwnIdLocale(serverLanguageTag='$serverLanguageTag', locale='$locale')"
}