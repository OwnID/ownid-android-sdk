package com.ownid.demo

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
 * Registers a Credential Manager-backed [SignInWithGoogle] handler on this registrar.
 *
 * Call this helper inside an OwnID providers block such as [OwnId.setProviders] or [OwnId.withProviders].
 *
 * Register Google Sign-In for OwnID OIDC flows using AndroidX Credential Manager.
 *
 * Register one helper instance per provider scope. That helper supports a single active `signIn(...)` request at a time.
 * If the same helper instance receives a second `signIn(...)` call before the first one finishes, the second call fails
 * immediately with `IllegalStateException("Another Google sign-in request is already in progress")`.
 *
 * Result mapping:
 * - Returns [SignInWithSocial.Result.Success] only when Google returns a non-empty ID token.
 * - Maps coroutine cancellation to [SignInWithSocial.Result.Canceled] with [Reason.UserClose].
 * - Returns [SignInWithSocial.Result.Fail] for Credential Manager cancellation. Credential Manager report the same
 *   cancellation error when the user dismisses the Google UI and when Google cannot complete authorization, for example
 *   because of app configuration or account state. Returning failure lets the app inspect and log the original error
 *   instead of treating every case as a user close.
 * - Returns [SignInWithSocial.Result.Fail] for configuration, credential parsing, or AndroidX failures.
 *
 * Sign-out:
 * - Clears local Google credential state via [CredentialManager.clearCredentialState] on `signOut`.
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
 * - This helper is not packaged into the SDK artifacts.
 * - The host target must add the Google Identity dependency required by Credential Manager Google Sign-In, including
 *   `com.google.android.libraries.identity.googleid:googleid`.
 *
 * @param credentialManagerProvider Optional factory for supplying a [CredentialManager].
 *
 * @return This registrar for chaining.
 */
fun OwnIdProvidersRegistrar.signInWithGoogleProvider(
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
