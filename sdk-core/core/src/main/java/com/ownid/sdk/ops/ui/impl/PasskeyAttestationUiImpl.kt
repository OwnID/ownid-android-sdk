package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.PasskeyAttestationUi

/**
 * Default Core adapter from operation UI to the registered platform passkey capability.
 *
 * The adapter preserves the [Passkey.Result] boundary: setup, UI-context, or provider failures are returned as
 * [Passkey.Result.Fail] for the operation to classify.
 */
internal class PasskeyAttestationUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val passkeyProvider: () -> Passkey,
) : PasskeyAttestationUi {

    override suspend fun createCredential(
        attestationOptions: AttestationOptions,
        isAutoSelectAllowed: Boolean
    ): Passkey.Result<AttestationResult> = runCatchingExceptions {
        val context = uiContextProvider.invoke().current()
        passkeyProvider.invoke().createCredential(context, attestationOptions, isAutoSelectAllowed)
    }.getOrElse { error -> Passkey.Result.Fail(error) }
}
