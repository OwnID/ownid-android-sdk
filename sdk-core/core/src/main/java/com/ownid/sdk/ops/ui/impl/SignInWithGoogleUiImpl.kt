package com.ownid.sdk.ops.ui.impl

import com.ownid.sdk.capability.GoogleSignInProvider
import com.ownid.sdk.capability.SocialSignIn
import com.ownid.sdk.capability.UiContextProvider
import com.ownid.sdk.model.runCatchingExceptions
import com.ownid.sdk.ops.ui.SignInWithGoogleUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class SignInWithGoogleUiImpl(
    private val uiContextProvider: () -> UiContextProvider,
    private val googleSignInProvider: () -> GoogleSignInProvider,
) : SignInWithGoogleUi {
    @Volatile
    private var activeProvider: GoogleSignInProvider? = null

    override suspend fun signIn(webClientId: String, nonce: String?): SocialSignIn.Result = runCatchingExceptions {
        currentCoroutineContext().ensureActive()
        val provider = googleSignInProvider.invoke()
        val params = SocialSignIn.Params(webClientId, nonce)
        val available = provider.isAvailable(params)
        currentCoroutineContext().ensureActive()
        if (!available) return@runCatchingExceptions SocialSignIn.Result.Fail(IllegalStateException("Google sign-in provider is unavailable."))
        val uiProvider = uiContextProvider.invoke()
        withContext(Dispatchers.Main.immediate) {
            when (val resolution = uiProvider.current()) {
                is UiContextProvider.Result.Success -> {
                    activeProvider = provider
                    try {
                        provider.signIn(resolution.activity, params)
                    } finally {
                        activeProvider = null
                    }
                }

                is UiContextProvider.Result.Failure -> SocialSignIn.Result.Fail(resolution.error)
            }
        }
    }.getOrElse { error -> SocialSignIn.Result.Fail(error) }

    override suspend fun cancel() {
        withContext(Dispatchers.Main.immediate) {
            activeProvider?.cancel()
        }
    }
}
