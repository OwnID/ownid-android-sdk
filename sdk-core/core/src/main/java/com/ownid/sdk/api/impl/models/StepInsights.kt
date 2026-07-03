package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Step-level analytics metrics collected during a flow. */
internal class StepInsights(
    /**
     * Duration in milliseconds
     */
    @SerialName("duration")
    val duration: Long? = null,

    /**
     * Number of retries recorded for the step.
     */
    @SerialName("retries")
    val retries: Int? = null,

    /**
     * Number of clicks recorded for the step.
     */
    @SerialName("clicksCount")
    val clicksCount: Int? = null
)
