package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.PasskeyAssertionUi

/**
 * Default Core adapter from operation UI to the registered platform passkey capability.
 *
 * The adapter preserves the [Passkey.Result] boundary: setup, UI-context, or provider failures are returned as
 * [Passkey.Result.Fail] for the operation to classify.
 */
internal class PasskeyAssertionUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val passkeyProvider: () -> Passkey,
) : PasskeyAssertionUi {

    override suspend fun getCredential(assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> = runCatchingExceptions {
        val context = uiContextProvider.invoke().current()
        passkeyProvider.invoke().getCredential(context, assertionOptions)
    }.getOrElse { error -> Passkey.Result.Fail(error) }
}
