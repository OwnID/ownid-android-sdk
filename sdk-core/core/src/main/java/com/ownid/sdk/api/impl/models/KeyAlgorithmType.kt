package com.ownid.sdk.api.impl.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The cryptographic algorithm to be used in COSE format
 */
@Serializable(with = KeyAlgorithmTypeSerializer::class)
internal enum class KeyAlgorithmType(val value: Int) {
    NUMBER_7(-7),
    NUMBER_257(-257);
}

internal object KeyAlgorithmTypeSerializer : KSerializer<KeyAlgorithmType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KeyAlgorithmType", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): KeyAlgorithmType {
        val raw = decoder.decodeInt()
        return KeyAlgorithmType.values().firstOrNull { it.value == raw }
            ?: throw SerializationException("Unknown KeyAlgorithmType value: $raw")
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithmType) {
        encoder.encodeInt(value.value)
    }
}
