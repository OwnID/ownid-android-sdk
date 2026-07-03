package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Event categories accepted by the analytics endpoint. */
internal enum class EventInfoType {
    @SerialName("journey-summary")
    JOURNEY_SUMMARY
}
