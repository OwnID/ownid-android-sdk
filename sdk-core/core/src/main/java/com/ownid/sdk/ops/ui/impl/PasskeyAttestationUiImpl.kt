package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.AttestationOptions
import com.ownid.sdk.model.AttestationResult
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.PasskeyAttestationUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Connects passkey creation requests from operations to the registered [Passkey] capability.
 *
 * Resolves a host Activity before delegating each request and returns the capability's result unchanged.
 * If host resolution fails, returns [Passkey.Result.Fail] with the same error without invoking the capability.
 */
internal class PasskeyAttestationUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val passkeyProvider: () -> Passkey,
) : PasskeyAttestationUi {

    override suspend fun createCredential(
        attestationOptions: AttestationOptions,
        isAutoSelectAllowed: Boolean
    ): Passkey.Result<AttestationResult> = runCatchingExceptions {
        val passkey = passkeyProvider.invoke()
        val uiProvider = uiContextProvider.invoke()
        withContext(Dispatchers.Main.immediate) {
            when (val resolution = uiProvider.current()) {
                is UiContextProvider.Result.Success -> passkey.createCredential(resolution.activity, attestationOptions, isAutoSelectAllowed)
                is UiContextProvider.Result.Failure -> Passkey.Result.Fail(resolution.error)
            }
        }
    }.getOrElse { error -> Passkey.Result.Fail(error) }
}
