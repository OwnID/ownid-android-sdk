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
import com.ownid.sdk.capability.GoogleSignInProvider
import com.ownid.sdk.capability.SocialSignIn
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
 * Registers a Credential Manager-backed [GoogleSignInProvider] handler on this registrar.
 *
 * Call this helper inside an OwnID providers block such as [OwnId.setProviders] or [OwnId.withProviders].
 *
 * Register Sign in with Google for OwnID OIDC flows using AndroidX Credential Manager.
 *
 * Register one helper instance per provider scope. That helper supports a single active `signIn(...)` request at a time.
 * If the same helper instance receives a second `signIn(...)` call before the first one finishes, the second call fails
 * immediately with `IllegalStateException("Another Sign in with Google request is already in progress")`.
 *
 * Result mapping:
 * - Returns [SocialSignIn.Result.Success] only when Google returns a non-empty ID token.
 * - Maps coroutine cancellation to [SocialSignIn.Result.Canceled] with [Reason.UserClose].
 * - Maps [GetCredentialCancellationException] to [SocialSignIn.Result.Fail].
 * - Returns [SocialSignIn.Result.Fail] for configuration, credential parsing, or AndroidX failures.
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
 * - `signIn(...)` uses the Activity passed by OwnID for Credential Manager UI in the same task stack.
 * - `signOut(...)` does not present UI, so an application context is acceptable.
 * - This helper is not packaged into the SDK artifacts.
 * - The host target must add the Google Identity dependency required by Credential Manager Sign in with Google, including
 *   `com.google.android.libraries.identity.googleid:googleid`.
 *
 * @param credentialManagerProvider Optional factory for supplying a [CredentialManager].
 *
 * @return This registrar for chaining.
 */
fun OwnIdProvidersRegistrar.googleSignInProvider(
    credentialManagerProvider: (Context) -> CredentialManager = { context -> CredentialManager.create(context) }
): OwnIdProvidersRegistrar = apply {
    @OptIn(InternalOwnIdAPI::class)
    val logger = getOrNull(OwnIdLogRouter::class.java)

    val activeRequestJob = AtomicReference<Job?>(null)

    @OptIn(InternalOwnIdAPI::class)
    googleSignIn {
        signIn { activity, params ->
            val requestJob = currentCoroutineContext()[Job]
            when {
                requestJob == null ->
                    SocialSignIn.Result.Fail(IllegalStateException("Sign in with Google request is missing coroutine Job"))

                !activeRequestJob.compareAndSet(null, requestJob) ->
                    SocialSignIn.Result.Fail(IllegalStateException("Another Sign in with Google request is already in progress"))

                else -> try {
                    withContext(Dispatchers.Main.immediate) {
                        runCatching {
                            val googleSignInOption = GetSignInWithGoogleOption.Builder(params.webClientId)
                                .setNonce(params.nonce)
                                .build()

                            val googleSignInRequest = GetCredentialRequest.Builder()
                                .addCredentialOption(googleSignInOption)
                                .build()

                            val result = credentialManagerProvider(activity).getCredential(activity, googleSignInRequest)

                            when (val credential = result.credential) {
                                is CustomCredential -> {
                                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        SocialSignIn.Result.Success(googleIdTokenCredential.id, googleIdTokenCredential.idToken)
                                    } else {
                                        throw IllegalStateException("Unsupported credential type: ${credential::class.java.name}")
                                    }
                                }

                                else -> throw IllegalStateException("Unsupported credential class: ${credential::class.java.name}")
                            }
                        }.getOrElse { cause ->
                            when (cause) {
                                is CancellationException ->
                                    SocialSignIn.Result.Canceled(Reason.UserClose("User canceled authorization"))

                                is GetCredentialCancellationException -> {
                                    logger?.logW(this, "googleSignIn", "Sign in with Google was not completed: ${cause.message}", cause)
                                    SocialSignIn.Result.Fail(cause)
                                }

                                else -> {
                                    logger?.logW(this, "googleSignIn", "Sign in with Google failed: ${cause.message}", cause)
                                    SocialSignIn.Result.Fail(cause)
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
            activeRequestJob.get()?.cancel(CancellationException("Sign in with Google canceled"))
        }

        signOut { params ->
            withContext(Dispatchers.Main.immediate) {
                try {
                    credentialManagerProvider(params.context).clearCredentialState(ClearCredentialStateRequest())
                    null
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    logger?.logW(this@googleSignInProvider, "signOut", "Google sign-out failed: ${cause.message}", cause)
                    cause
                }
            }
        }
    }
}
