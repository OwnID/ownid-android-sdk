package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class UserJourneySummary(
    /**
     * Unique identifier of the journey
     */
    @SerialName("id")
    val id: String,

    /**
     * Traceparent header for distributed tracing
     */
    @SerialName("traceparent")
    val traceparent: String,

    @SerialName("reporter")
    val reporter: Reporter,

    @SerialName("eventInfo")
    val eventInfo: EventInfo,

    @SerialName("deviceInfo")
    val deviceInfo: ClientDeviceInfo,

    @SerialName("userInfo")
    val userInfo: List<UserInfo>
)
