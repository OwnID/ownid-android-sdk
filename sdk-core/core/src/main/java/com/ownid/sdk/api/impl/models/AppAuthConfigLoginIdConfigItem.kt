package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/**
 * Login ID type configuration entry from the auth config.
 */
internal class AppAuthConfigLoginIdConfigItem(
    /**
     * Configured login ID type.
     */
    @SerialName("type")
    val type: LoginIdType,

    /**
     * Optional validation regex override for this login ID type.
     */
    @SerialName("regex")
    val regex: String? = null
)
