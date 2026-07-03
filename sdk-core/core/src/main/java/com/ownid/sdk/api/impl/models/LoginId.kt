package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Login ID payload with the raw identifier and its classified type. */
internal class LoginId(
    /**
     * Raw login identifier value.
     */
    @SerialName("id")
    val id: String,

    /**
     * Classified login ID type.
     */
    @SerialName("type")
    val type: LoginIdType
)
