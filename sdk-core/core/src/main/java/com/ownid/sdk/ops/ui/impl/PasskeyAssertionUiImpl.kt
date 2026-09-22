package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.Passkey
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.AssertionOptions
import com.ownid.sdk.model.AssertionResult
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.PasskeyAssertionUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Connects passkey authentication requests from operations to the registered [Passkey] capability.
 *
 * Resolves a host Activity before delegating each request and returns the capability's result unchanged.
 * If host resolution fails, returns [Passkey.Result.Fail] with the same error without invoking the capability.
 */
internal class PasskeyAssertionUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val passkeyProvider: () -> Passkey,
) : PasskeyAssertionUi {

    override suspend fun getCredential(assertionOptions: AssertionOptions): Passkey.Result<AssertionResult> = runCatchingExceptions {
        val passkey = passkeyProvider.invoke()
        val uiProvider = uiContextProvider.invoke()
        withContext(Dispatchers.Main.immediate) {
            when (val resolution = uiProvider.current()) {
                is UiContextProvider.Result.Success -> passkey.getCredential(resolution.activity, assertionOptions)
                is UiContextProvider.Result.Failure -> Passkey.Result.Fail(resolution.error)
            }
        }
    }.getOrElse { error -> Passkey.Result.Fail(error) }
}
