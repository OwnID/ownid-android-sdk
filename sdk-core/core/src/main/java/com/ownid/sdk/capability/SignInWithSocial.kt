package com.ownid.sdk.capability

import android.content.Context
import com.ownid.sdk.model.Reason
import kotlinx.serialization.Serializable

/**
 * Social sign-in capability contract for provider integrations.
 *
 * Implementations launch provider UI through [signIn] and return [Result.Success], [Result.Canceled], or [Result.Fail].
 * The provider owns provider SDK setup, UI presentation, failure mapping, and any local provider session state.
 *
 * [isAvailable] defaults to `true`. Override it when provider state, app configuration, UI context, or request
 * parameters can make social sign-in unavailable.
 *
 * OwnID may call these suspend functions from SDK coroutine contexts; this contract does not guarantee the Android
 * main thread.
 *
 * Cancellation via [cancel] is best-effort and depends on provider API support.
 * OwnID treats [Result.Canceled] as cancellation and [Result.Fail] as a provider-owned failure.
 */
public interface SignInWithSocial : Capability {

    /**
     * Parameters for a social sign-in request.
     *
     * @property context Android framework context forwarded to the registered provider. UI-based providers commonly
     * require an active `Activity` context.
     * @property webClientId OAuth web client ID expected by the provider challenge.
     * @property nonce Optional OIDC nonce that the provider should bind to the returned ID token when supported.
     */
    public class Params(
        public val context: Context,
        public val webClientId: String,
        public val nonce: String? = null
    ) : Capability.Params

    /**
     * Outcome of a social sign-in attempt.
     *
     * The provider chooses the result category. Return [Success] only after obtaining an OIDC ID token; OwnID treats
     * [Canceled] as cancellation and [Fail] as a provider-side failure.
     */
    public sealed interface Result {
        /**
         * Successful sign-in containing provider identity data.
         *
         * @property id Provider user identifier metadata from the returned social credential. This value is optional
         *   for OwnID completion and may be empty when the provider does not supply one.
         * @property idToken OIDC ID token required to complete social sign-in. Treat this value as sensitive
         *   authentication data.
         */
        @Serializable
        public class Success(public val id: String, public val idToken: String) : Result

        /**
         * Sign-in failed before an ID token was obtained.
         *
         * @property error Provider SDK, credential, presentation, or configuration failure.
         */
        public class Fail(public val error: Throwable) : Result

        /**
         * Sign-in was canceled by the user, system UI, or provider.
         *
         * @property reason Cancellation reason for the social sign-in attempt.
         */
        public class Canceled(public val reason: Reason) : Result
    }

    /**
     * Starts a provider challenge and presents social sign-in UI.
     *
     * The provider validates [webClientId] and may bind [nonce] to the issued ID token. The nonce value is
     * server-provided challenge material; avoid logging or persisting it.
     *
     * OwnID passes [context] directly to the registered provider and does not retain it. Providers that require Android
     * UI APIs must use a suitable UI context and dispatch to the main thread when their SDK requires it. Return [Result.Fail]
     * for provider SDK, credential, presentation, or configuration failures, and avoid logging or persisting ID tokens or
     * nonces.
     *
     * @param params Social sign-in parameters.
     *
     * @return Sign-in outcome with credential data, cancellation details, or failure details.
     */
    public suspend fun signIn(params: Params): Result

    /**
     * Requests cancellation of an active social sign-in request.
     *
     * Support is provider-dependent and may be a no-op when the provider SDK has no explicit cancellation hook.
     * Providers should stop presenting provider UI when possible and return [Result.Canceled] from [signIn] when cancellation
     * is observed. Throwing from this method is logged or ignored by SDK cleanup paths.
     */
    public suspend fun cancel()
}

/**
 * Google-backed social sign-in capability.
 *
 * Register Google support through the OwnID provider registrar. This capability extends [SignInWithSocial] with
 * [signOut] for app-owned Google session cleanup.
 *
 * For [signIn], `webClientId` is the Google OAuth web client ID and `nonce` is forwarded to Google Identity as an
 * optional OIDC nonce when the provider supports it.
 *
 * [cancel] remains best-effort. Providers that require Android UI APIs must dispatch to the main thread when their SDK
 * requires it.
 */
public interface SignInWithGoogle : SignInWithSocial {

    /**
     * Parameters for [signOut].
     *
     * @property context Android framework context forwarded to the Google provider for local sign-out work.
     */
    public class SignOutParams(public val context: Context) : Capability.Params

    /**
     * Clears local Google sign-in state owned by the provider.
     *
     * This is local cleanup for the app/provider integration. It does not revoke OAuth consent grants or clear OwnID
     * session state unless the provider does so explicitly.
     *
     * @param params Google sign-out parameters.
     * @return `null` on success; otherwise the provider cleanup failure.
     */
    public suspend fun signOut(params: SignOutParams): Throwable?
}
