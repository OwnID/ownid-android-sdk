package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Analytics event payload containing event type and tracked flows. */
internal class EventInfo(
    /**
     * Event category.
     */
    @SerialName("type")
    val type: EventInfoType,

    /**
     * Flows included in the event payload.
     */
    @SerialName("flows")
    val flows: List<Flow>
)
