package com.ownid.sdk

import androidx.annotation.VisibleForTesting
import com.ownid.sdk.exception.OwnIdException
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

/**
 * Represent OwnID Data in OwnID flows.
 */
public class OwnIdPayload @VisibleForTesting @InternalOwnIdAPI constructor(
    @JvmField public val type: Type,
    @JvmField public val data: String,
    @JvmField public val metadata: String
) : Serializable {

    public enum class Type { Registration, Login }

    @InternalOwnIdAPI
    internal companion object {
        @JvmSynthetic
        @Throws(JSONException::class, OwnIdException::class)
        internal fun fromJson(json: JSONObject): OwnIdPayload {
            if (json.has("error")) throw OwnIdException(json.optString("error"))

            val type = when (val typeString = json.optString("type")) {
                "registrationInfo" -> Type.Registration
                "session" -> Type.Login
                else -> throw OwnIdException("Unexpected payload type: '$typeString'")
            }
            return OwnIdPayload(type, json.optString("data"), json.optString("metadata"))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdPayload
        if (type != other.type) return false
        if (data != other.data) return false
        return metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    override fun toString(): String = "OwnIdPayload(type=$type, data=*, metadata=*)"
}