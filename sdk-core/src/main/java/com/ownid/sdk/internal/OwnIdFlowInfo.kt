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
 */
public class OwnIdFlowInfo @VisibleForTesting @InternalOwnIdAPI constructor(public val event: Event) {

    public enum class Event(public val value: String) {
        Register("register"), Login("login"), Unknown("unknown")
    }

    @InternalOwnIdAPI
    internal companion object {
        private const val KEY_EVENT = "event"

        @JvmStatic
        @JvmSynthetic
        @Throws(JSONException::class)
        internal fun fromJson(json: JSONObject): OwnIdFlowInfo {
            val event = when (json.optString(KEY_EVENT)) {
                Event.Register.value -> Event.Register
                Event.Login.value -> Event.Login
                else -> Event.Unknown
            }
            return OwnIdFlowInfo(event)
        }
    }

    @JvmSynthetic
    @InternalOwnIdAPI
    internal fun asJson(): JSONObject =
        JSONObject()
            .put(KEY_EVENT, event.value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OwnIdFlowInfo
        if (event != other.event) return false
        return true
    }

    override fun hashCode(): Int {
        return event.hashCode()
    }
}