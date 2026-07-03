package com.ownid.sdk.ops.ui

import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.ops.OperationUi

/**
 * UI capability for presenting the platform passkey creation (attestation) prompt.
 *
 * This capability is the boundary between SDK-managed operations and AndroidX Credential Manager. It does not own
 * OwnID challenge state or verification; callers provide server-issued [AttestationOptions] and receive the platform
 * passkey outcome as [Passkey.Result].
 */
public interface PasskeyAttestationUi : OperationUi {
    /**
     * Presents the Credential Manager UI to create a new passkey.
     *
     * The platform credential provider owns prompt presentation, credential creation, cancellation, and provider-level
     * errors. The returned [Passkey.Result] preserves that boundary so the operation layer can decide whether to
     * verify, cancel, or fail the OwnID challenge.
     *
     * @param attestationOptions Server-provided options for the FIDO2 attestation ceremony.
     * @param isAutoSelectAllowed If `true`, allows auto-selection when a single account matches.
     * @return The attestation result on success, or [Passkey.Result] indicating cancellation or failure. Provider or
     *   UI errors are surfaced as [Passkey.Result.Fail].
     */
    public suspend fun createCredential(attestationOptions: AttestationOptions, isAutoSelectAllowed: Boolean): Passkey.Result<AttestationResult>
}
