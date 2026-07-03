package com.ownid.sdk.ui.locale

import com.ownid.sdk.model.LanguageTag
import com.ownid.sdk.model.decodeBase64UrlToByteArray
import com.ownid.sdk.model.encodeToBase64UrlSafe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * Server locale payload for one [LanguageTag].
 *
 * `null` content represents a locale entry that is temporarily unavailable and must not be used for string lookup.
 * Callers use [isExpired] to decide whether the entry should be refreshed and [getString] or [hasString] to resolve
 * server-provided copy before falling back to embedded strings.
 *
 * Lookup treats all keys except the last one as the JSON object path. The final key is resolved from the deepest
 * available object back to the root, preferring `<key>-android` before the shared `<key>` at each level.
 */
@Serializable
internal data class ServerLocaleContent(
    val languageTag: LanguageTag,
    @Serializable(with = Base64UrlSafeJsonObjectSerializer::class)
    val content: JsonObject? = null,
    val timeStamp: Long = System.currentTimeMillis(),
    val backoffUntil: Long? = null,
) {
    internal companion object {
        private const val ANDROID_SUFFIX: String = "-android"
        private val LOCALE_CACHE_TIME: Long = TimeUnit.MINUTES.toMillis(10)
    }

    internal object Base64UrlSafeJsonObjectSerializer : KSerializer<JsonObject?> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Base64UrlSafeJsonObject", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: JsonObject?) {
            if (value == null) {
                encoder.encodeString("")
                return
            }
            encoder.encodeString(value.toString().encodeToByteArray().encodeToBase64UrlSafe(noPadding = false))
        }

        override fun deserialize(decoder: Decoder): JsonObject? {
            val base64 = decoder.decodeString()
            if (base64.isEmpty()) return null
            return Json.parseToJsonElement(String(base64.decodeBase64UrlToByteArray(), Charsets.UTF_8)).jsonObject
        }
    }

    /** Returns whether the entry should be refreshed, honoring an active missing-locale backoff window. */
    internal fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (backoffUntil?.let { nowMillis < it } == true) return false
        return nowMillis - timeStamp > LOCALE_CACHE_TIME
    }

    /** Returns whether a string exists for [localeKeys] using the server locale lookup rules. */
    internal fun hasString(vararg localeKeys: String): Boolean = findStringValue(localeKeys) != null

    /** Returns a string for [localeKeys], or throws when no matching server value exists. */
    @Throws
    internal fun getString(vararg localeKeys: String): String =
        findStringValue(localeKeys) ?: throw IllegalStateException("No value found for keys: ${localeKeys.joinToString()}")

    private fun findStringValue(localeKeys: Array<out String>): String? {
        if (content == null || localeKeys.isEmpty()) return null

        val valueKey = localeKeys.last()
        val valueKeyAndroid = valueKey + ANDROID_SUFFIX
        val pathKeys = localeKeys.dropLast(1)

        val parentObjects = mutableListOf<JsonObject>()
        var currentObject: JsonObject? = content
        parentObjects.add(content)
        for (key in pathKeys) {
            currentObject = currentObject?.get(key)?.jsonObject
            if (currentObject != null) {
                parentObjects.add(currentObject)
            } else {
                break
            }
        }

        for (parent in parentObjects.asReversed()) {
            parent[valueKeyAndroid]?.jsonPrimitive?.content?.let { return it }
            parent[valueKey]?.jsonPrimitive?.content?.let { return it }
        }

        return null
    }
}
