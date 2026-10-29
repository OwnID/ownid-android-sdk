package com.ownid.sdk.api.impl.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
internal class ResendPolicy(
    /**
     * Indicates whether resending is allowed.
     */
    @SerialName("allow")
    val allow: Boolean,

    /**
     * The maximum number of resends allowed.
     */
    @SerialName("attempts")
    @Serializable(with = ResendAttemptsSerializer::class)
    val attempts: Int,

    /**
     * The delay in seconds before another resend can be made.
     */
    @SerialName("debounce")
    val debounce: Int
)

private object ResendAttemptsSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ResendPolicy.attempts", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
        val number = primitive?.takeUnless { it.isString }?.doubleOrNull
        if (number == null || !number.isFinite() || number < 0 || number > 10) invalidAttempts()

        val attempts = number.toInt()
        if (number != attempts.toDouble()) invalidAttempts()
        return attempts
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    private fun invalidAttempts(): Nothing =
        throw SerializationException("ResendPolicy.attempts must be an integer between 0 and 10.")
}
