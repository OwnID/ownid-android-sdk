package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.SignInWithGoogleUi

internal class SignInWithGoogleUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val signInWithGoogleProvider: () -> SignInWithGoogle,
) : SignInWithGoogleUi {
    private var activeProvider: SignInWithGoogle? = null

    override suspend fun signIn(webClientId: String, nonce: String?): SignInWithSocial.Result = runCatchingExceptions {
        val context = uiContextProvider.invoke().current()
        val provider = signInWithGoogleProvider.invoke()
        activeProvider = provider
        try {
            provider.signIn(SignInWithSocial.Params(context, webClientId, nonce))
        } finally {
            activeProvider = null
        }
    }.getOrElse { error -> SignInWithSocial.Result.Fail(error) }

    override suspend fun cancel() {
        activeProvider?.cancel()
    }
}
