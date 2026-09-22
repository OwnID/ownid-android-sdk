package com.ownid.sdk.ops.ui

import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.ops.OperationUi
import com.ownid.sdk.ops.SignInWithGoogleOperation

/**
 * Google sign-in UI for [SignInWithGoogleOperation].
 *
 * Host-resolution and sign-in failures are reported as [SignInWithSocial.Result.Fail].
 * UI presentation is not guaranteed.
 */
public interface SignInWithGoogleUi : OperationUi {
    /**
     * Attempts Google sign-in and returns its outcome.
     *
     * @param webClientId The OAuth web client ID configured for Google Sign-In.
     * @param nonce Optional OIDC nonce. OwnID passes the active challenge ID so the provider can bind it to the returned
     * ID token when supported.
     *
     * @return The sign-in result containing the ID token, a provider cancellation, or a provider/UI failure surfaced as
     * [SignInWithSocial.Result.Fail].
     */
    public suspend fun signIn(webClientId: String, nonce: String? = null): SignInWithSocial.Result

    /** Requests best-effort cancellation of an in-progress Google Sign-In request. */
    public suspend fun cancel()
}
