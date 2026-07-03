package com.ownid.sdk.capability

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * JSON serialization and deserialization capability.
 *
 * Encodes values to JSON strings or [JsonElement] and decodes them back using the configured [Json] instance.
 * It does not redact or encrypt encoded values; callers that serialize tokens, login IDs, or user data own where those
 * strings are stored, logged, or sent.
 */
public interface Serialization : Capability {
    /** The underlying [Json] instance used for all operations. */
    public val json: Json

    /**
     * Encodes [value] to a JSON string using the given [serializer].
     *
     * @throws SerializationException on encoding failure.
     */
    @Throws(SerializationException::class)
    public fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String =
        json.encodeToString(serializer, value)

    /**
     * Encodes [value] to a [JsonElement] using the given [serializer].
     *
     * @throws SerializationException on encoding failure.
     */
    @Throws(SerializationException::class)
    public fun <T> encodeToJsonElement(serializer: SerializationStrategy<T>, value: T): JsonElement =
        json.encodeToJsonElement(serializer, value)

    /**
     * Decodes a [T] from a JSON [string] using the given [deserializer].
     *
     * @throws SerializationException on decoding failure.
     * @throws IllegalArgumentException if the input is not valid JSON.
     */
    @Throws(SerializationException::class, IllegalArgumentException::class)
    public fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T =
        json.decodeFromString(deserializer, string)

    /**
     * Decodes a [T] from a [JsonElement] using the given [deserializer].
     *
     * @throws SerializationException on decoding failure.
     * @throws IllegalArgumentException if the input is not valid JSON.
     */
    @Throws(SerializationException::class, IllegalArgumentException::class)
    public fun <T> decodeFromJsonElement(deserializer: DeserializationStrategy<T>, jsonElement: JsonElement): T =
        json.decodeFromJsonElement(deserializer, jsonElement)

    /**
     * Parses a JSON [string] into a [JsonElement] without deserializing to a typed object.
     *
     * @throws SerializationException on parsing failure.
     */
    @Throws(SerializationException::class)
    public fun parseToJsonElement(string: String): JsonElement =
        json.parseToJsonElement(string)
}
