package com.ownid.sdk.internal

import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import org.json.JSONException
import org.json.JSONObject

/**
 * Represent OwnID Data in different flows.
 */
public class OwnIdPayload @VisibleForTesting @InternalOwnIdAPI constructor(
    public val type: Type,
    public val ownIdData: String,
    public val metadata: String
) {

    public enum class Type(public val value: String) {
        Registration("registrationInfo"), Login("session"), Unknown("unknown")
    }

    @InternalOwnIdAPI
    internal companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_DATA = "data"
        private const val KEY_METADATA = "metadata"

        @JvmStatic
        @JvmSynthetic
        @Throws(JSONException::class)
        internal fun fromJson(json: JSONObject): OwnIdPayload {
            val type = when (json.optString(KEY_TYPE)) {
                Type.Registration.value -> Type.Registration
                Type.Login.value -> Type.Login
                else -> Type.Unknown
            }
            return OwnIdPayload(type, json.optString(KEY_DATA), json.optString(KEY_METADATA))
        }
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun asJson(): JSONObject =
        JSONObject()
            .put(KEY_TYPE, type.value)
            .put(KEY_DATA, ownIdData)
            .put(KEY_METADATA, metadata)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdPayload
        if (type != other.type) return false
        if (ownIdData != other.ownIdData) return false
        if (metadata != other.metadata) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + ownIdData.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}