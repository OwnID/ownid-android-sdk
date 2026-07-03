package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Default decoding handles standard typed fields only. `sessionPayload` is intentionally skipped with
 * `@Transient` and then injected in `LoginApiCall` after reading the original HTTP body. Structured
 * values remain JSON text; JSON string scalars are decoded to their string value.
 *
 * Raw extraction reads the first top-level `sessionPayload` field. If duplicate root keys are present,
 * only the first occurrence is used.
 */
@Serializable
internal class LoginResponse(
    /**
     * A signed JWT that can be verified as a proof of successful operations
     */
    @SerialName("accessToken")
    val accessToken: String,

    /**
     * The session payload, could be of any type, depending on the app and integrations
     *
     * This field is not read by default kotlinx deserialization and is populated manually in
     * `LoginApiCall` after extracting the first top-level value from the response body. Structured
     * values remain JSON text; JSON string scalars are decoded to their string value.
     */
    @Transient
    val sessionPayload: String = ""
)
