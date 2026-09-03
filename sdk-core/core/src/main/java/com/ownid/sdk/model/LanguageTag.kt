package com.ownid.sdk.model

import com.ownid.sdk.model.LanguageTag.LanguageTagSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Locale

/**
 * A language tag with a [language] code and optional [country] code.
 *
 * Serializes to and from its [tagString] representation (e.g. "en", "en-US"). Values that do not resolve to a usable
 * language fall back to [LanguageTag.DEFAULT].
 */
@Serializable(with = LanguageTagSerializer::class)
public class LanguageTag internal constructor(
    public val language: String,
    public val country: String
) {
    public companion object {
        public val DEFAULT: LanguageTag = LanguageTag("en", "")

        internal fun fromLocale(locale: Locale): LanguageTag {
            val language = when (val lang = locale.language.lowercase(Locale.ROOT)) {
                "iw" -> "he"
                "in" -> "id"
                "ji" -> "yi"
                else -> lang
            }
            if (language.isBlank() || language == "und") return DEFAULT
            val country = locale.country.uppercase(Locale.ROOT)
            return LanguageTag(language, country)
        }
    }

    internal object LanguageTagSerializer : KSerializer<LanguageTag> {
        override val descriptor = PrimitiveSerialDescriptor("LanguageTag", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: LanguageTag) {
            encoder.encodeString(value.tagString)
        }

        override fun deserialize(decoder: Decoder): LanguageTag =
            fromLocale(Locale.forLanguageTag(decoder.decodeString()))
    }

    public fun toLanguageOnly(): LanguageTag = LanguageTag(language, "")

    @Transient
    public val tagString: String = if (country.isBlank()) language else "$language-$country"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LanguageTag) return false
        return language == other.language && country == other.country
    }

    override fun hashCode(): Int = 31 * language.hashCode() + country.hashCode()

    override fun toString(): String = tagString
}
