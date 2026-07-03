package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Single step recorded inside a tracked authentication flow. */
internal class Step(
    /**
     * Operation type represented by this step.
     */
    @SerialName("operationType")
    val operationType: OperationType,

    /**
     * Optional step name.
     */
    @SerialName("name")
    val name: String? = null,

    /**
     * Step status.
     */
    @SerialName("status")
    val status: StepStatus,

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

    /**
     * Client-side errors captured for this step.
     */
    @SerialName("errors")
    val errors: List<ClientError>? = null,

    /**
     * Step-level analytics metrics.
     */
    @SerialName("insights")
    val insights: StepInsights? = null
)
