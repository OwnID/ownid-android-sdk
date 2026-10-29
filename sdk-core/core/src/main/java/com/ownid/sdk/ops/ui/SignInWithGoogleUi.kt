package com.ownid.sdk.ops.ui

import com.ownid.sdk.capability.SocialSignIn
import com.ownid.sdk.ops.OperationUi
import com.ownid.sdk.ops.SignInWithGoogleOperation

/**
 * Sign in with Google UI for [SignInWithGoogleOperation].
 *
 * Host-resolution and sign-in failures are reported as [SocialSignIn.Result.Fail].
 * UI presentation is not guaranteed.
 */
public interface SignInWithGoogleUi : OperationUi {
    /**
     * Attempts Sign in with Google and returns its outcome.
     *
     * @param webClientId The OAuth web client ID configured for Sign in with Google.
     * @param nonce Optional OIDC nonce. OwnID passes the active challenge ID so the provider can bind it to the returned
     * ID token when supported.
     *
     * @return The sign-in result containing the ID token, a provider cancellation, or a provider/UI failure surfaced as
     * [SocialSignIn.Result.Fail].
     */
    public suspend fun signIn(webClientId: String, nonce: String? = null): SocialSignIn.Result

    /** Requests best-effort cancellation of an in-progress Sign in with Google request. */
    public suspend fun cancel()
}
