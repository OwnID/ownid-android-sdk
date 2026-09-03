package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class AuthRequirements(
    /**
     * Discrete score for an operation
     */
    @SerialName("targetScore")
    val targetScore: Int,

    /**
     * Sorted list of recommended operations that can be performed to reach the target score
     */
    @SerialName("operations")
    val operations: List<OperationRequirement>
) {
    init {
        require(targetScore >= 0) { "Target score must be non-negative." }
    }
}
