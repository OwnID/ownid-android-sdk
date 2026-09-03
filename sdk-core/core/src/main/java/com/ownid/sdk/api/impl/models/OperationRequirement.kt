package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Recommended authentication operation returned by auth requirements. */
internal class OperationRequirement(
    /**
     * Operation type to perform.
     */
    @SerialName("type")
    val type: OperationType,

    /**
     * Discrete score for an operation
     */
    @SerialName("score")
    val score: Int,

    /**
     * List of available channels with ids to perform the operation
     */
    @SerialName("channels")
    val channels: List<OperationChannel>? = null
) {
    init {
        require(score >= 0) { "Operation score must be non-negative." }
    }
}
