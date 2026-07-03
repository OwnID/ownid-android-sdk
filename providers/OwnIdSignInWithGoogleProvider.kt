package com.ownid.sdk.provider

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ownid.sdk.OwnId
import com.ownid.sdk.capability.SignInWithGoogle
import com.ownid.sdk.capability.SignInWithSocial
import com.ownid.sdk.logger.OwnIdLogRouter
import com.ownid.sdk.model.InternalOwnIdAPI
import com.ownid.sdk.model.Reason
import com.ownid.sdk.provider.OwnIdProvidersRegistrar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Registers a Credential Manager-backed Google sign-in helper on this registrar.
 *
 * Register this source-only helper inside an OwnID providers block such as [OwnId.setProviders] or
 * [OwnId.withProviders]. It registers [SignInWithGoogle] through [OwnIdProvidersRegistrar.signInWithGoogle], so OwnID
 * can resolve Google social sign-in from the scope where this helper is registered.
 *
 * The helper relies on AndroidX Credential Manager and Google Identity Services classes from the host target. Add the
 * Credential Manager dependencies required by your app and `com.google.android.libraries.identity.googleid:googleid`.
 *
 * Behavior:
 * - Uses the Google web client ID and optional nonce supplied in the provider callback parameters.
 * - Forwards the nonce to `GetSignInWithGoogleOption`; treat ID tokens and nonces as sensitive authentication data.
 * - Reports available for Google sign-in parameters.
 * - Dispatches Credential Manager `getCredential` and `clearCredentialState` calls to [Dispatchers.Main.immediate].
 * - Supports one active `signIn(...)` request per helper instance. Overlapping requests fail immediately with
 *   `IllegalStateException("Another Google sign-in request is already in progress")`.
 *
 * Result mapping:
 * - Returns [SignInWithSocial.Result.Success] only when Google returns a non-empty ID token.
 * - Maps coroutine cancellation to [SignInWithSocial.Result.Canceled] with [Reason.UserClose].
 * - Returns [SignInWithSocial.Result.Fail] for Credential Manager cancellation so provider, account, or configuration
 *   failures surfaced through that exception remain visible to the app instead of being collapsed into user close.
 * - Returns [SignInWithSocial.Result.Fail] for configuration, credential parsing, or AndroidX failures.
 *
 * Sign-out:
 * - Clears local Google credential state via [CredentialManager.clearCredentialState] on `signOut`.
 * - This is local provider/app cleanup. It does not revoke OAuth consent grants or clear OwnID session state.
 *
 * Cancellation:
 * - `cancel()` targets the currently active `signIn(...)` call on this helper instance, if one exists.
 * - AndroidX Credential Manager propagates coroutine cancellation to the underlying request.
 * - After `cancel()`, a new `signIn(...)` call is still rejected until the canceled request actually finishes unwinding.
 *
 * Context and dependency notes:
 * - `signIn(...)` uses the [Context] passed by OwnID. Use an active `Activity` context so the Credential Manager UI
 *   stays in the same task stack.
 * - `signOut(...)` does not present UI, so an application context is acceptable.
 * - This source-only helper is not packaged into the SDK artifacts and is not a separate product.
 *
 * @param credentialManagerProvider Optional factory for supplying a [CredentialManager]. The helper invokes it on the
 *   main dispatcher for both sign-in and sign-out.
 *
 * @return This registrar for chaining.
 */
public fun OwnIdProvidersRegistrar.signInWithGoogleProvider(
    credentialManagerProvider: (Context) -> CredentialManager = { context -> CredentialManager.create(context) }
): OwnIdProvidersRegistrar = apply {
    @OptIn(InternalOwnIdAPI::class)
    val logger = getOrNull(OwnIdLogRouter::class.java)

    val activeRequestJob = AtomicReference<Job?>(null)

    @OptIn(InternalOwnIdAPI::class)
    signInWithGoogle {
        signIn { params ->
            val requestJob = currentCoroutineContext()[Job]
            when {
                requestJob == null ->
                    SignInWithSocial.Result.Fail(IllegalStateException("Google sign-in request is missing coroutine Job"))

                !activeRequestJob.compareAndSet(null, requestJob) ->
                    SignInWithSocial.Result.Fail(IllegalStateException("Another Google sign-in request is already in progress"))

                else -> try {
                    withContext(Dispatchers.Main.immediate) {
                        runCatching {
                            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(params.webClientId)
                                .setNonce(params.nonce)
                                .build()

                            val signInWithGoogleRequest = GetCredentialRequest.Builder()
                                .addCredentialOption(signInWithGoogleOption)
                                .build()

                            val result = credentialManagerProvider(params.context).getCredential(params.context, signInWithGoogleRequest)

                            when (val credential = result.credential) {
                                is CustomCredential -> {
                                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        SignInWithSocial.Result.Success(googleIdTokenCredential.id, googleIdTokenCredential.idToken)
                                    } else {
                                        throw IllegalStateException("Unsupported credential type: ${credential::class.java.name}")
                                    }
                                }

                                else -> throw IllegalStateException("Unsupported credential class: ${credential::class.java.name}")
                            }
                        }.getOrElse { cause ->
                            when (cause) {
                                is CancellationException ->
                                    SignInWithSocial.Result.Canceled(Reason.UserClose("User canceled authorization"))

                                is GetCredentialCancellationException -> {
                                    logger?.logW(this, "signInWithGoogle", "Google sign-in was not completed: ${cause.message}", cause)
                                    SignInWithSocial.Result.Fail(cause)
                                }

                                else -> {
                                    logger?.logW(this, "signInWithGoogle", "Google sign-in failed: ${cause.message}", cause)
                                    SignInWithSocial.Result.Fail(cause)
                                }
                            }
                        }
                    }
                } finally {
                    activeRequestJob.compareAndSet(requestJob, null)
                }
            }
        }

        cancel {
            activeRequestJob.get()?.cancel(CancellationException("Google Sign-In canceled"))
        }

        signOut { params ->
            withContext(Dispatchers.Main.immediate) {
                try {
                    credentialManagerProvider(params.context).clearCredentialState(ClearCredentialStateRequest())
                    null
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    logger?.logW(this@signInWithGoogleProvider, "signOut", "Google sign-out failed: ${cause.message}", cause)
                    cause
                }
            }
        }
    }
}
