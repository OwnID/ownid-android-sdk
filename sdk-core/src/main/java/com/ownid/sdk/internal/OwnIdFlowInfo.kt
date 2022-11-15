package com.ownid.sdk.internal

import androidx.annotation.VisibleForTesting
import com.ownid.sdk.InternalOwnIdAPI
import com.ownid.sdk.internal.OwnIdFlowInfo.Event
import org.json.JSONException
import org.json.JSONObject

/**
 * Represent OwnID flow information.
 *
 * @param event       [Event] type data. It's either [Event.Register] or [Event.Login]
 * @param authType    A string describing the type of authentication that was used during OwnID flow
 */
public class OwnIdFlowInfo @VisibleForTesting @InternalOwnIdAPI constructor(public val event: Event, public val authType: String) {

    public enum class Event(public val value: String) {
        Register("register"), Login("login"), Unknown("unknown")
    }

    @InternalOwnIdAPI
    internal companion object {
        private const val KEY_EVENT = "event"
        private const val KEY_AUTH_TYPE = "authType"

        @JvmStatic
        @JvmSynthetic
        @Throws(JSONException::class)
        internal fun fromJson(json: JSONObject): OwnIdFlowInfo {
            val event = when (json.optString(KEY_EVENT)) {
                Event.Register.value -> Event.Register
                Event.Login.value -> Event.Login
                else -> Event.Unknown
            }

            val authType = json.optString(KEY_AUTH_TYPE)

            return OwnIdFlowInfo(event, authType)
        }
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun asJson(): JSONObject =
        JSONObject()
            .put(KEY_EVENT, event.value)
            .put(KEY_AUTH_TYPE, authType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdFlowInfo
        if (event != other.event) return false
        if (authType != other.authType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = event.hashCode()
        result = 31 * result + authType.hashCode()
        return result
    }

    override fun toString(): String = "OwnIdFlowInfo(event=$event, authType='$authType')"
}