package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class Flow(
    /**
     * Unique identifier for the flow
     */
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("source")
    val source: FlowSource,

    @SerialName("status")
    val status: FlowStatus,

    /**
     * UTC timestamp
     */
    @SerialName("startedAt")
    val startedAt: String,

    /**
     * UTC timestamp
     */
    @SerialName("completedAt")
    val completedAt: String? = null,

    @SerialName("errors")
    val errors: List<ClientError>? = null,

    @SerialName("switchedToFlow")
    val switchedToFlow: String? = null,

    @SerialName("insights")
    val insights: FlowInsights? = null,

    @SerialName("steps")
    val steps: List<Step>
)
