package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Source that initiated a tracked flow. */
internal enum class FlowSource {
    @SerialName("widget-button")
    WIDGET_BUTTON,

    @SerialName("returning-user-prompt")
    RETURNING_USER_PROMPT,

    @SerialName("recovery-prompt")
    RECOVERY_PROMPT,

    @SerialName("enroll-prompt")
    ENROLL_PROMPT,

    @SerialName("elite")
    ELITE,

    @SerialName("agent-authorizing")
    AGENT_AUTHORIZING,

    @SerialName("deferred")
    DEFERRED,

    @SerialName("explicit")
    EXPLICIT,

    @SerialName("implicit")
    IMPLICIT
}
