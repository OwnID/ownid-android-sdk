package com.ownid.sdk.ops.ui

import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.ops.OperationUi
import com.ownid.sdk.ops.SignInWithGoogleOperation

/**
 * UI capability used by [SignInWithGoogleOperation] to present Google Sign-In.
 *
 * This capability is backed by the app-registered [SignInWithGoogle] provider. Core forwards
 * the OIDC client ID, nonce, and current UI context to that provider and consumes the returned
 * [SignInWithSocial.Result].
 */
public interface SignInWithGoogleUi : OperationUi {
    /**
     * Presents Google Sign-In and returns the provider result.
     *
     * @param webClientId The OAuth web client ID configured for Google Sign-In.
     * @param nonce Optional OIDC nonce. OwnID passes the active challenge ID so the provider can bind it to the returned
     * ID token when supported.
     * @return The sign-in result containing the ID token, a provider cancellation, or a provider/UI failure surfaced as
     * [SignInWithSocial.Result.Fail].
     */
    public suspend fun signIn(webClientId: String, nonce: String? = null): SignInWithSocial.Result

    /** Requests best-effort cancellation of an in-progress Google Sign-In request. */
    public suspend fun cancel()
}
