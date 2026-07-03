package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Cancellation reason codes used by challenge cancellation requests. */
internal enum class CancelReason {
    @SerialName("timeout")
    TIMEOUT,

    @SerialName("userClose")
    USER_CLOSE,

    @SerialName("moveToOtherChallenge")
    MOVE_TO_OTHER_CHALLENGE,

    @SerialName("systemError")
    SYSTEM_ERROR,

    @SerialName("unknown")
    UNKNOWN,

    @SerialName("alreadyExists")
    ALREADY_EXISTS
}
