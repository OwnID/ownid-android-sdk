package com.ownid.sdk.model

import kotlinx.serialization.Serializable

/**
 * Identifies an OwnID challenge.
 *
 * The value is serialized as a single string. The wrapper does not validate, trim, or redact the identifier; callers
 * that accept external input remain responsible for rejecting blank values when they are not valid there.
 */
@JvmInline
@Serializable
public value class ChallengeId(public val value: String) {
    override fun toString(): String = value
}
