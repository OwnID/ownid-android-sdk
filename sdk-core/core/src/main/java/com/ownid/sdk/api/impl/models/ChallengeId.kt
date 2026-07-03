package com.ownid.sdk.api.impl.models

import kotlinx.serialization.Serializable

/**
 * The challenge's identifier
 */
@JvmInline
@Serializable
internal value class ChallengeId(val value: String)
