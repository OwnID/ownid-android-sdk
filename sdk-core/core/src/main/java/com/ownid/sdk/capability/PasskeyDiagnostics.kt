package com.ownid.sdk.capability

import com.ownid.sdk.OwnId

/**
 * Runs best-effort diagnostic checks for passkey support against a relying-party ID.
 *
 * Diagnostics are intentionally fire-and-forget and are not part of operation/controller lifecycles.
 * In-flight checks may continue after [OwnId.destroy], and no cancellation handle is exposed.
 * Results are diagnostic log output only; they do not block, authorize, or retry passkey operations.
 */
internal interface PasskeyDiagnostics : Capability {
    /**
     * Starts passkey diagnostics for [rpId].
     *
     * The call returns immediately; results are reported asynchronously through logs.
     */
    fun verify(rpId: String)
}
