package com.ownid.sdk.ops.ui

import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.ops.OperationUi

/**
 * UI capability for presenting the platform passkey authentication (assertion) prompt.
 *
 * This capability is the boundary between SDK-managed operations and AndroidX Credential Manager. It does not own
 * OwnID challenge state or verification; callers provide server-issued [AssertionOptions] and receive the platform
 * passkey outcome as [Passkey.Result].
 */
public interface PasskeyAssertionUi : OperationUi {
    /**
     * Presents the Credential Manager UI to authenticate with an existing passkey.
     *
     * The platform credential provider owns prompt presentation, credential selection, cancellation, and provider-level
     * errors. The returned [Passkey.Result] preserves that boundary so the operation layer can decide whether to
     * verify, cancel, or fail the OwnID challenge.
     *
     * @param assertionOptions Server-provided options for the FIDO2 assertion ceremony.
     * @return The assertion result on success, or [Passkey.Result] indicating cancellation or failure. Provider or UI
     *   errors are surfaced as [Passkey.Result.Fail].
     */
    public suspend fun getCredential(assertionOptions: AssertionOptions): Passkey.Result<AssertionResult>
}
