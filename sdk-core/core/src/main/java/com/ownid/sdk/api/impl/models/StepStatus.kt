package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Lifecycle status values recorded for a tracked step. */
internal enum class StepStatus {
    @SerialName("aborted")
    ABORTED,

    @SerialName("in-progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED
}
