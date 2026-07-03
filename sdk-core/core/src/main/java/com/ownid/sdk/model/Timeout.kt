package com.ownid.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Timeout in milliseconds, normalized to zero or greater.
 *
 * Negative constructor and decoded values are clamped to `0`. The value serializes as a single integer containing the
 * normalized millisecond count.
 */
@Serializable(with = Timeout.Serializer::class)
public class Timeout(milliseconds: Long) {
    public val milliseconds: Long = milliseconds.coerceAtLeast(0)

    override fun toString(): String = milliseconds.toString()

    override fun equals(other: Any?): Boolean = this === other || (other is Timeout && milliseconds == other.milliseconds)

    override fun hashCode(): Int = milliseconds.hashCode()

    public object Serializer : KSerializer<Timeout> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Timeout", PrimitiveKind.LONG)

        override fun serialize(encoder: Encoder, value: Timeout): Unit = encoder.encodeLong(value.milliseconds)

        override fun deserialize(decoder: Decoder): Timeout = Timeout(decoder.decodeLong())
    }
}
