package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * About: The active challenge limit or concurrency guard was reached.
 *
 * End-user: Ask the user to wait briefly or finish an existing challenge before
 * starting another.
 *
 * Developer action: Rate-limit retries, log repeated occurrences, and inspect
 * challenge cleanup if this persists.
 */
@Serializable
internal class MaximumChallengesReachedErrorResponse(
    @SerialName("errorCode")
    val errorCode: ErrorCode,

    /**
     * Human-readable diagnostic message.
     */
    @SerialName("message")
    val message: String
)
