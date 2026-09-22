package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.SignInWithGoogleUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SignInWithGoogleUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val signInWithGoogleProvider: () -> SignInWithGoogle,
) : SignInWithGoogleUi {
    @Volatile
    private var activeProvider: SignInWithGoogle? = null

    override suspend fun signIn(webClientId: String, nonce: String?): SignInWithSocial.Result = runCatchingExceptions {
        val provider = signInWithGoogleProvider.invoke()
        val uiProvider = uiContextProvider.invoke()
        withContext(Dispatchers.Main.immediate) {
            when (val resolution = uiProvider.current()) {
                is UiContextProvider.Result.Success -> {
                    activeProvider = provider
                    try {
                        provider.signIn(resolution.activity, SignInWithSocial.Params(webClientId, nonce))
                    } finally {
                        activeProvider = null
                    }
                }

                is UiContextProvider.Result.Failure -> SignInWithSocial.Result.Fail(resolution.error)
            }
        }
    }.getOrElse { error -> SignInWithSocial.Result.Fail(error) }

    override suspend fun cancel() {
        withContext(Dispatchers.Main.immediate) {
            activeProvider?.cancel()
        }
    }
}
