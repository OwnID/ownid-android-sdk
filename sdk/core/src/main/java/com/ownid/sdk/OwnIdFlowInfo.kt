package com.ownid.sdk

import androidx.annotation.VisibleForTesting
import org.json.JSONObject
import java.io.Serializable

/**
 * Represent OwnID flow information.
 *
 * @param event       [Event] type data
 * @param authType    A string describing the type of authentication that was used during OwnID flow
 * @param authToken   A token that can be used to refresh a session
 */
public class OwnIdFlowInfo @VisibleForTesting @InternalOwnIdAPI constructor(
    @JvmField public val event: Event,
    @JvmField public val authType: String,
    @JvmField public val authToken: String?
) : Serializable {

    public enum class Event { Register, Login, Unknown }

    @InternalOwnIdAPI
    internal companion object {
        @JvmSynthetic
        internal fun fromJson(json: JSONObject): OwnIdFlowInfo {
            val eventString = json.optString("event")
            val event = Event.values().firstOrNull { it.name.equals(eventString, ignoreCase = true) } ?: Event.Unknown
            return OwnIdFlowInfo(event, json.optString("authType"), json.optString("authToken").ifBlank { null })
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdFlowInfo
        if (event != other.event) return false
        if (authType != other.authType) return false
        return authToken == other.authToken
    }

    override fun hashCode(): Int {
        var result = event.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + (authToken?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "OwnIdFlowInfo(event=$event, authType='$authType', authToken='${authToken?.let { it.take(8) + "..." }}')"
}