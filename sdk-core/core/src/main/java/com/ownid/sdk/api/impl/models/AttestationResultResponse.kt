package com.ownid.sdk.api.impl.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Default decoding handles standard typed fields only. `ownIdData` is intentionally skipped with
 * `@Transient` and then injected in `AttestationResultApiCall` after reading the original HTTP body.
 * Structured values remain JSON text; JSON string scalars are decoded to their string value.
 *
 * Raw extraction reads the first top-level `ownIdData` field. If duplicate root keys are present,
 * only the first occurrence is used.
 */
@Serializable
internal class AttestationResultResponse(
    /**
     * ownidData object to replace the data in the vendor's db
     *
     * This field is not read by default kotlinx deserialization and is populated manually in
     * `AttestationResultApiCall` after extracting the first top-level value from the response body.
     * Structured values remain JSON text; JSON string scalars are decoded to their string value.
     */
    @Transient
    val ownIdData: String = "",

    /**
     * A signed JWT that can be verified as a proof of successful single operation
     */
    @SerialName("proofToken")
    val proofToken: String
)
